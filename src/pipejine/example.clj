(ns pipejine.example
  (:require [pipejine.logger :as log]
            [pipejine.core :as pipe]))

;;   q1
;; /   \
;; q2  q3
;;  \ /
;;  q4


(defn pipeline []
  (let [q1 (pipe/new-queue {:queue-size 5
                             :number-of-consumer-threads 5,
                             :number-of-producers 1})
        q2 (pipe/new-queue {:queue-size 2
                             :number-of-consumer-threads 1,
                             :number-of-producers 1
                             :partition 2})                 ;; partition queues should only have one thread!
        q3 (pipe/new-queue {:queue-size 3
                             :number-of-consumer-threads 3,
                             :number-of-producers 1})
        q4 (pipe/new-queue {:queue-size 10
                             :number-of-consumer-threads 1,
                             :number-of-producers 2
                             :partition :all
                             :debug true})]

    (def q1 q1)
    (def q2 q2)
    (def q3 q3)
    (def q4 q4)

    (pipe/spawn-consumers q1 #(do
                                 (pipe/produce q2 (inc %)) ;; q1 workers puts stuff on q2
                                 (pipe/produce q3 (dec %)) ;; .. and q2
                                 ))

    (pipe/spawn-consumers q2 #(do
                                 (log/log (str "q2 got: " %))
                                 (doseq [d %]
                                   (pipe/produce q4 (* d d)))))

    (pipe/spawn-consumers q3 #(do
                                 ;; (throw (Exception. "blah"))
                                 (Thread/sleep 10)
                                 (pipe/produce q4 (/ % 2))))

    (pipe/spawn-consumers q4 #(log/log (str "** " %)))

    (dotimes [i 20]                                          ;; Seed q1 with data
      (Thread/sleep 10)
      (log/log (str "prod: " i))
      (pipe/produce q1 i)

      (when (= i 5)                               ;; unexpected error
        (pipe/shutdown q1)))

    (pipe/produce-done q1)                                  ;; Mark that we're done putting data in q1

    ;; (pipe/chain-queues #(log/log "pipeline exhausted!") q1 q2 q4)

    (pipe/producer-of q1 q2 q3)
    (pipe/producer-of q2 q4)
    (pipe/producer-of q3 q4)
    (pipe/spawn-supervisor q4 #(log/log "pipeline exhausted!"))

    ))

(comment

  (pipeline)
)
