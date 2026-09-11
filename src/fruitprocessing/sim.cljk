(ns fruitprocessing.sim
  "Simple simulation/demo driver for the fruit & vegetable processing actor.
  Exercises the basic flow: intake -> log batch -> coordinate shipment.

  For CLI: clojure -M:dev:run"
  (:require [fruitprocessing.store :as store]
            [fruitprocessing.operation :as operation]))

(defn -main [& _args]
  (println "Fruit & Vegetable Processing Actor Simulation")
  (println "==============================================")

  ;; Set up an initial batch in the store
  (let [st (store/mem-store
            {:initial-batches
             {"batch-1030-001"
              {:id "batch-1030-001"
               :jurisdiction "US"
               :product-type "frozen-peas"
               :received-at "2026-07-14T08:00:00Z"
               :batch-temp-c -20.0
               :storage-time-days 3
               :harvest-lot-verified? true
               :sanitation-score 88
               :residue-screening {:passed? true :test-date-valid? true :lab-accredited? true}
               :spoilage-flag-raised? false
               :spoilage-flag-resolved? true
               :evidence-checklist [:harvest-lot-record :temperature-log
                                    :storage-time-record :sanitation-log
                                    :residue-screening-pass :traceability-record]
               :processed? false}}})
        actor-context {:actor-id "fruit-vegetable-processor-01" :phase :phase-1}]

    ;; Simulate logging a production batch
    (println "\n1. Logging production batch: batch-1030-001")
    (let [request {:op :log-production-batch
                   :subject "batch-1030-001"
                   :stake :log-production-batch}
          result (operation/run-operation st request actor-context)]
      (println "   Disposition:" (:disposition result))
      (println "   Audit trail:" (:audit result))
      (when-let [rec (:record result)]
        (println "   Record:" rec)))

    ;; Simulate coordinating shipment (only if prior logged successfully)
    (println "\n2. Coordinating shipment: batch-1030-001")
    (let [request {:op :coordinate-shipment
                   :subject "batch-1030-001"
                   :stake :coordinate-shipment}
          result (operation/run-operation st request actor-context)]
      (println "   Disposition:" (:disposition result))
      (println "   Audit trail:" (:audit result))
      (when-let [rec (:record result)]
        (println "   Record:" rec)))

    (println "\nSimulation complete")))
