(ns pipejine.core
  (:require [clojure.tools.logging :as log]
            [clojure.stacktrace :as st])
  (:import [java.util.concurrent LinkedBlockingQueue CountDownLatch TimeUnit]))

(defn new-queue
  "Create and initialize a new consumer queue"
  [{:keys [name queue-size number-of-consumer-threads number-of-producers partition time-out]}]
  (let [number-of-consumer-threads (or number-of-consumer-threads 1)
        number-of-producers (or number-of-producers 1)]
    {:q (LinkedBlockingQueue. (or queue-size 1))
     :name (or name (gensym "queue"))
     :consumers-done (CountDownLatch. number-of-consumer-threads)
     :producers-done (CountDownLatch. number-of-producers)
     :part (or partition 0)                              ;; :all for gathering everything (one consumer thread only!)
     :thread-num number-of-consumer-threads
     :run (atom true)
     :time-out (or time-out 500)}))

(defn produce
  "Produce some data into queue"
  [{:keys [q run time-out]} d]
  (loop [r false]                                 ;; don't put anything on the queue when aborted,
    (when (and d (not r) @run)                    ;; this is to avoid blocking producing threads
      (recur (.offer q d time-out TimeUnit/MILLISECONDS)))))

(defn produce-done
  "Tell a queue that no more data will be produced, each producer should call this only once"
  [{:keys [producers-done]}]
  (.countDown producers-done))

(defn shutdown [{:keys [run producers-done] :as q}]
  (reset! run false)
  ;; drain latch to release the supervisors
  (while (not (zero? (.getCount producers-done)))
    (produce-done q)))

(defn- consumer [{:keys [q consumers-done producers-done part run time-out]} f]
  (let [deliver (fn [d acc]
                  (let [acc (if d (conj acc d) acc)]
                    (cond
                     ;; No partitioning, just deliver d
                     (and (number? part) (zero? part))
                     (do
                       (when d (f d))
                       [])

                     ;; Partition filled, deliver it
                     (= (count acc) part)
                     (do
                       (f acc)
                       [])

                     ;; Keep accumulating
                     :default
                     acc)))
        stop? (fn []
                (or
                 (not @run)                                ;; stop flag
                 (and (zero? (.getCount producers-done))   ;; producers done and q empty
                      (zero? (.size q)))))]
    (try
      (loop [acc []]
        (let [d (.poll q time-out TimeUnit/MILLISECONDS)
              acc (deliver d acc)]
          (if (stop?)
            (when-not (zero? (count acc)) (f acc))         ;; deliver any outstanding data before quitting
            (recur acc))))
      (catch Exception e
        (let [writer (java.io.StringWriter.)]
          (binding [*out* writer]
            (st/print-stack-trace e)
            (log/error (str writer)))))
      (finally
        (.countDown consumers-done)))))

(defn- supervisor [{:keys [consumers-done producers-done] :as q} f]
  (.await consumers-done)
  (shutdown q)                                             ;; if all consumers have died, we shutdown the queue
  (.await producers-done)
  (f))

(defn spawn-consumers
  "Spawn consumer threads for a queue, function f will be called on each data item consumed"
  [{:keys [thread-num] :as q} f]
  (dotimes [_ thread-num]
    (future (consumer q f))))

(defn spawn-supervisor
  "Spawn a supervisor thread for a queue, function f will be called when the consumers are done
   with all items put into the queue (will only happen once).
   Please note that multiple supervisors can be spawned per queue"
  [q f]
  (future (supervisor q f)))

(defn producer-of
  "Mark q1 as producer of qs"
  [q1 & qs]
  (spawn-supervisor q1 #(doseq [q qs] (produce-done q))))

;; -------------------------------------------
;; Helpers

(defn prod-fn
  "Returns a function used to produce data into a queue"
  [q]
  (fn [d] (produce q d)))

(defn read-seq
  "Returns a lazy-seq with data consumed from a q. To be used *INSTEAD OF* spawn-consumers"
  [{:keys [run time-out] :as q}]
  (let [nq (LinkedBlockingQueue.)]        ;; we need a new queue here in order to use q's partitioning
    (spawn-consumers q (fn [d] (.put nq d)))
    (spawn-supervisor q (constantly true))
    ((fn s []
       (lazy-seq (loop [d nil]
                   (when @run
                     (if d
                       (cons d (s))
                       (recur (.poll nq time-out TimeUnit/MILLISECONDS))))))))))

(defn chain-queues
  "Spawn supervisors for a chain of queues (non-branching pipeline) so that the function f is called
  when the final queue in the chain has been fully consumed"
  [f & qs]
  (doseq [[q1 q2] (partition 2 1 qs)]
    (spawn-supervisor q1 #(produce-done q2)))
  (spawn-supervisor (last qs) f))

(defn spawn-logger [& qs]
  "Spawn a watcher thread of supplied queues. Will stop when all qs are shut down or
  the returned shutdown function is called."
  (let [running (atom true)
        log-fn (fn []
                 (while (and @running (some #(-> % :run deref) qs))
                   (do
                     (Thread/sleep 1000)
                     (log/info
                      (apply str
                             "------------------------------\n"
                             (for [{:keys [name run q producers-done consumers-done]} qs]
                               (format "%-15.15s [r]%-5.5b [q]%-4d [p#]%-2d [c#]%-2d\n"
                                       name @run
                                       (.size q) (.getCount producers-done)
                                       (.getCount consumers-done))))))))]
    (.start (Thread. log-fn))
    (fn [] (reset! running false))))
