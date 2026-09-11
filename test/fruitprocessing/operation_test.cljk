(ns fruitprocessing.operation-test
  (:require [clojure.test :refer [deftest is testing]]
            [fruitprocessing.operation :as operation]
            [fruitprocessing.store :as store]))

(def ^:private clean-batch
  {:batch-temp-c -20.0
   :product-type "frozen-peas"
   :storage-time-days 3
   :jurisdiction "US"
   :sanitation-score 85
   :residue-screening {:passed? true :test-date-valid? true :lab-accredited? true}
   :spoilage-flag-raised? false
   :evidence-checklist [:harvest-lot-record :temperature-log
                        :storage-time-record :sanitation-log
                        :residue-screening-pass :traceability-record]})

(deftest run-operation-phase-1-escalates-high-stakes
  (testing "Clean batch log-production-batch escalates in phase-1 (high-stakes)"
    (let [st (store/mem-store {:initial-batches {"batch-1" clean-batch}})
          request {:op :log-production-batch
                   :subject "batch-1"
                   :stake :log-production-batch}
          context {:actor-id "test-fruitveg" :phase :phase-1}
          result (operation/run-operation st request context)]
      (is (= :escalate (:disposition result)))
      (is (some? (:audit result)))
      (is (seq (:audit result)))
      (is (nil? (:record result))))))

(deftest run-operation-governor-hold
  (testing "Governor rejection (unresolved spoilage flag) produces HOLD disposition"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" (assoc clean-batch
                                  :spoilage-flag-raised? true
                                  :spoilage-flag-resolved? false)}})
          request {:op :log-production-batch
                   :subject "batch-2"
                   :stake :log-production-batch}
          context {:actor-id "test-fruitveg"}
          result (operation/run-operation st request context)]
      (is (= :hold (:disposition result)))
      (is (some? (:audit result)))
      (is (nil? (:record result))))))

(deftest run-operation-unregistered-batch-holds
  (testing "Coordinating shipment for an unregistered batch is a HARD hold"
    (let [st (store/mem-store)
          request {:op :coordinate-shipment
                   :subject "no-such-batch"
                   :stake :coordinate-shipment}
          context {:actor-id "test-fruitveg"}
          result (operation/run-operation st request context)]
      (is (= :hold (:disposition result)))
      (is (some #(= :batch-not-registered (:rule %)) (get-in result [:verdict :violations]))))))

(deftest run-operation-flag-food-safety-concern-always-escalates
  (testing "Flagging a food-safety concern always escalates, even when clean"
    (let [st (store/mem-store {:initial-batches {"batch-1" clean-batch}})
          request {:op :flag-food-safety-concern
                   :subject "batch-1"
                   :stake :monitoring}
          context {:actor-id "test-fruitveg" :phase :phase-3}
          result (operation/run-operation st request context)]
      (is (= :escalate (:disposition result))))))

(deftest run-operation-schedule-maintenance-commits-when-clean
  (testing "Routine maintenance scheduling commits directly when clean and confident"
    (let [st (store/mem-store)
          request {:op :schedule-maintenance
                   :subject "MD-002"
                   :stake :operational}
          context {:actor-id "test-fruitveg" :phase :phase-3}
          result (operation/run-operation st request context)]
      (is (= :commit (:disposition result)))
      (is (some? (:record result)))
      (is (= :propose (:effect (:record result)))))))
