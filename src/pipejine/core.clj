(ns pipejine.core
  (:require [clojure.stacktrace :as st])
  (:import [java.util.concurrent LinkedBlockingQueue CountDownLatch TimeUnit]))

;; TODO - potential problems
;; 1/ If all consumers crash, items will be left on the queue,
;;    and the "done" signal will trickle through

(defn new-queue
  "Create and initialize a new consumer queue"
  [{:keys [queue-size number-of-consumer-threads number-of-producers partition time-out debug]}]
  (let [queue-size (or queue-size 1)
        number-of-consumer-threads (or number-of-consumer-threads 1)
        number-of-producers (or number-of-producers 1)
        partition (or partition 0)
        time-out (or time-out 500)]
    {:q (LinkedBlockingQueue. queue-size)
     :consumers-done (CountDownLatch. number-of-consumer-threads)
     :producers-done (CountDownLatch. number-of-producers)
     :part partition                              ;; :all for gathering everything (one consumer thread only!)
     :thread-num number-of-consumer-threads
     :run (atom true)
     :time-out time-out
     :debug debug}))

(defn produce
  "Produce some data into queue"
  [{:keys [q run time-out]} d]
  (loop [r false]                                 ;; don't put anything on the queue when aborted,
    (when (and (not r) @run)                      ;; this is to avoid blocking producing threads
      (recur (.offer q d time-out TimeUnit/MILLISECONDS)))))

(defn produce-done
  "Tell a queue that no more data will be produced, each producer should call this only once"
  [{:keys [producers-done]}]
  (.countDown producers-done))

(defn shutdown [{:keys [run producers-done] :as q}]
  (reset! run false)
  ;; drain latch to release the supervisor
  (while (not (zero? (.getCount producers-done)))
    (produce-done q)))

(defn- consumer [{:keys [q consumers-done producers-done part debug run time-out]} f]
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
        (st/print-stack-trace e))
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
   will all items put into the queue (will only happen once).
   Please note that only one supoervisor can be spawned per queue"
  [q f]
  (future (supervisor q f)))

(defn producer-of
  "Mark q1 as producer of qs"
  [q1 & qs]
  (spawn-supervisor q1 #(doseq [q qs] (produce-done q))))

(defn chain-queues
  "Spawn supervisors for a chain of queues (non-branching pipeline) so that the function f is called
  when the final queue in the chain has been fully consumed"
  [f & qs]
  (doseq [[q1 q2] (partition 2 1 qs)]
    (spawn-supervisor q1 #(produce-done q2)))
  (spawn-supervisor (last qs) f))
