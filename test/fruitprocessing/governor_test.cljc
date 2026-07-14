(ns fruitprocessing.governor-test
  (:require [clojure.test :refer [deftest is are testing]]
            [fruitprocessing.governor :as governor]
            [fruitprocessing.store :as store]
            [fruitprocessing.facts :as facts]))

(deftest spec-basis-violations
  (testing "No spec-basis in value -> violation"
    (let [proposal {:cites [] :value {}}
          request {:op :log-production-batch}
          violations (#'governor/spec-basis-violations request proposal)]
      (is (seq violations))
      (is (= :no-spec-basis (-> violations first :rule)))))

  (testing "Spec-basis provided -> no violation"
    (let [proposal {:cites ["FSIS-123"] :value {:jurisdiction "US"}}
          request {:op :log-production-batch}
          violations (#'governor/spec-basis-violations request proposal)]
      (is (empty? violations))))

  (testing "Only applies to real operations"
    (let [proposal {:cites [] :value {}}
          request {:op :schedule-maintenance}  ; not a real op
          violations (#'governor/spec-basis-violations request proposal)]
      (is (empty? violations)))))

(deftest batch-temp-out-of-range-violations
  (testing "Temperature within range -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:batch-temp-c 4.0
                           :product-type "fresh-tomatoes"}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (empty? violations))))

  (testing "Temperature below range -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" {:batch-temp-c -2.0
                           :product-type "fresh-tomatoes"}}})
          request {:op :log-production-batch :subject "batch-2"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (seq violations))
      (is (= :batch-temp-out-of-range (-> violations first :rule)))))

  (testing "Temperature above range -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-3" {:batch-temp-c 15.0
                           :product-type "fresh-lettuce"}}})
          request {:op :log-production-batch :subject "batch-3"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (seq violations))
      (is (= :batch-temp-out-of-range (-> violations first :rule))))))

(deftest storage-time-exceeded-violations
  (testing "Storage time within limit -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:storage-time-days 3 :jurisdiction "US"}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/storage-time-exceeded-violations request st)]
      (is (empty? violations))))

  (testing "Storage time exceeds limit -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" {:storage-time-days 12 :jurisdiction "US"}}})
          request {:op :log-production-batch :subject "batch-2"}
          violations (#'governor/storage-time-exceeded-violations request st)]
      (is (seq violations))
      (is (= :storage-time-exceeded (-> violations first :rule))))))

(deftest spoilage-flag-unresolved-violations
  (testing "No spoilage flag -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:spoilage-flag-raised? false}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/spoilage-flag-unresolved-violations request st)]
      (is (empty? violations))))

  (testing "Spoilage raised but resolved -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" {:spoilage-flag-raised? true
                           :spoilage-flag-resolved? true}}})
          request {:op :log-production-batch :subject "batch-2"}
          violations (#'governor/spoilage-flag-unresolved-violations request st)]
      (is (empty? violations))))

  (testing "Spoilage raised and NOT resolved -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-3" {:spoilage-flag-raised? true
                           :spoilage-flag-resolved? false}}})
          request {:op :log-production-batch :subject "batch-3"}
          violations (#'governor/spoilage-flag-unresolved-violations request st)]
      (is (seq violations))
      (is (= :spoilage-flag-unresolved (-> violations first :rule))))))

(deftest check-ok-verdict
  (testing "All checks pass -> ok? true"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:batch-temp-c 4.0
                           :product-type "fresh-tomatoes"
                           :storage-time-days 3
                           :jurisdiction "US"
                           :sanitation-score 85
                           :residue-screening {:passed? true :test-date-valid? true :lab-accredited? true}
                           :spoilage-flag-raised? false
                           :evidence-checklist [:harvest-origin :temperature-log
                                               :storage-time-record :sanitation-log
                                               :residue-screening-pass
                                               :traceability-record]}}})
          request {:op :log-production-batch :subject "batch-1" :stake :log-production-batch}
          proposal {:value {:jurisdiction "US"} :cites ["USDA"] :confidence 0.85}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (true? (:ok? verdict)))
      (is (empty? (:violations verdict))))))

(deftest check-hard-violations
  (testing "Hard violations -> ok? false, hard? true"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:spoilage-flag-raised? true
                           :spoilage-flag-resolved? false
                           :product-type "fresh-apples"}}})
          request {:op :log-production-batch :subject "batch-1" :stake :log-production-batch}
          proposal {:value {} :cites [] :confidence 0.85}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:hard? verdict)))
      (is (seq (:violations verdict))))))

(deftest already-processed-check
  (testing "Batch already processed -> violation"
    (let [st (store/mem-store
              {:initial-batches {"batch-1" {:processed? true}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/already-processed-violations request st)]
      (is (seq violations))
      (is (= :already-processed (-> violations first :rule))))))
