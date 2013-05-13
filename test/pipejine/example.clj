(ns pipejine.example
  (:require [clojure.tools.logging :as log]
            [pipejine.core :as pipe]))

;;   q1
;; /   \
;; q2  q3
;;  \ /
;;  q4


(defn pipeline []
  (let [q1 (pipe/new-queue {:name "q1"
                             :queue-size 5
                             :number-of-consumer-threads 5
                             :number-of-producers 1})
        q2 (pipe/new-queue {:name "q2"
                             :queue-size 2
                             :number-of-consumer-threads 1
                             :number-of-producers 1
                             :partition 2})                  ;; partition queues should only have one thread!
        q3 (pipe/new-queue {:name "q3"
                             :queue-size 3
                             :number-of-consumer-threads 3
                             :number-of-producers 1})
        q4 (pipe/new-queue {:name "q4"
                             :queue-size 10
                             :number-of-consumer-threads 1
                             :number-of-producers 2
                             :partition :all
                             :debug true})
        logger (pipe/spawn-logger q1 q2 q3 q4)]

    (pipe/spawn-consumers q1 #(do
                                 (pipe/produce q2 (inc %))  ;; q1 workers puts stuff on q2
                                 (pipe/produce q3 (dec %))  ;; .. and q3
                                 ))

    (pipe/spawn-consumers q2 #(do
                                 (log/info "q2 got: " %)
                                 (doseq [d %]
                                   (pipe/produce q4 (* d d)))))

    (pipe/spawn-consumers q3 #(do
                                 (Thread/sleep 10)
                                 (pipe/produce q4 (/ % 2))))

    (pipe/producer-of q1 q2 q3)
    (pipe/producer-of q2 q4)
    (pipe/producer-of q3 q4)
    (pipe/spawn-supervisor q4 #(log/info "pipeline exhausted!"))

    ;; example of read-seq, could just as well be another consumer (as above)
    (future (log/info "***" (first (pipe/read-seq q4))))

    (dotimes [i 20]                                          ;; Seed q1 with data
      (Thread/sleep 10)
      (log/info "prod: " i)
      (pipe/produce q1 i)

      ;; unexpected error
      (when (= i 5)                                          
        (pipe/shutdown q1)))

    (pipe/produce-done q1)                                  ;; Mark that we're done putting data in q1

    ;; (logger)
    ))

(comment
  (pipeline)
)
