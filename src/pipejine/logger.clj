(ns pipejine.logger)

;; for example purposes only

(def the-logger (agent 0))

(defn log [m]
  (let [id (.getId (Thread/currentThread))]
    (send the-logger (fn [c]
                       (println id m)
                       (inc c)))))
