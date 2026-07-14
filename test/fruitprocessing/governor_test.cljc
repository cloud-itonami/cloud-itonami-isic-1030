(ns fruitprocessing.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [fruitprocessing.governor :as governor]
            [fruitprocessing.store :as store]))

;; ──────────────────────── Closed Op-Allowlist ──────────────────────

(deftest op-not-allowed-violations-test
  (testing "operation outside the closed allowlist is a hard violation"
    (let [request {:op :operate-retort :subject "batch-1"}
          violations (#'governor/op-not-allowed-violations request {})]
      (is (seq violations))
      (is (= :op-not-allowed (-> violations first :rule)))))

  (testing "food-safety certification is outside the allowlist"
    (let [request {:op :certify-food-safety :subject "batch-1"}
          violations (#'governor/op-not-allowed-violations request {})]
      (is (seq violations))
      (is (= :op-not-allowed (-> violations first :rule)))))

  (testing "all four allowed ops pass"
    (doseq [op [:log-production-batch :schedule-maintenance
                :flag-food-safety-concern :coordinate-shipment]]
      (let [request {:op op :subject "batch-1"}
            violations (#'governor/op-not-allowed-violations request {})]
        (is (empty? violations) (str op " should be allowed"))))))

;; ──────────────────────── :effect must be :propose ──────────────────────

(deftest effect-not-propose-violations-test
  (testing "proposal asserting a non-:propose effect is a hard violation"
    (let [proposal {:effect :commit}
          violations (#'governor/effect-not-propose-violations {} proposal)]
      (is (seq violations))
      (is (= :effect-not-propose (-> violations first :rule)))))

  (testing "proposal with :effect :propose passes"
    (let [proposal {:effect :propose}
          violations (#'governor/effect-not-propose-violations {} proposal)]
      (is (empty? violations))))

  (testing "proposal with no :effect key passes (advisor omitted it)"
    (let [proposal {}
          violations (#'governor/effect-not-propose-violations {} proposal)]
      (is (empty? violations)))))

;; ──────────────────────── Batch Must Be Registered ──────────────────────

(deftest batch-not-registered-violations-test
  (testing ":log-production-batch on an unregistered batch is a hard violation"
    (let [st (store/mem-store)
          request {:op :log-production-batch :subject "no-such-batch"}
          violations (#'governor/batch-not-registered-violations request st)]
      (is (seq violations))
      (is (= :batch-not-registered (-> violations first :rule)))))

  (testing ":coordinate-shipment on an unregistered batch is a hard violation"
    (let [st (store/mem-store)
          request {:op :coordinate-shipment :subject "no-such-batch"}
          violations (#'governor/batch-not-registered-violations request st)]
      (is (seq violations))
      (is (= :batch-not-registered (-> violations first :rule)))))

  (testing ":flag-food-safety-concern on an unregistered batch is a hard violation"
    (let [st (store/mem-store)
          request {:op :flag-food-safety-concern :subject "no-such-batch"}
          violations (#'governor/batch-not-registered-violations request st)]
      (is (seq violations))
      (is (= :batch-not-registered (-> violations first :rule)))))

  (testing "registered batch passes"
    (let [st (store/mem-store {:initial-batches {"batch-1" {:id "batch-1"}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/batch-not-registered-violations request st)]
      (is (empty? violations))))

  (testing ":schedule-maintenance is not batch-scoped -- no violation even if subject unregistered"
    (let [st (store/mem-store)
          request {:op :schedule-maintenance :subject "equipment-9"}
          violations (#'governor/batch-not-registered-violations request st)]
      (is (empty? violations)))))

;; ──────────────────────── Spec Basis ──────────────────────

(deftest spec-basis-violations-test
  (testing "No spec-basis in value -> violation"
    (let [proposal {:cites [] :value {}}
          request {:op :log-production-batch}
          violations (#'governor/spec-basis-violations request proposal)]
      (is (seq violations))
      (is (= :no-spec-basis (-> violations first :rule)))))

  (testing "Spec-basis provided -> no violation"
    (let [proposal {:cites ["FDA-FSMA"] :value {:jurisdiction "US"}}
          request {:op :log-production-batch}
          violations (#'governor/spec-basis-violations request proposal)]
      (is (empty? violations))))

  (testing "Only applies to batch-scoped operations"
    (let [proposal {:cites [] :value {}}
          request {:op :schedule-maintenance}
          violations (#'governor/spec-basis-violations request proposal)]
      (is (empty? violations)))))

;; ──────────────────────── Storage Temperature ──────────────────────

(deftest batch-temp-out-of-range-violations-test
  (testing "Temperature within range -> no violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" {:batch-temp-c -20.0
                           :product-type "frozen-peas"}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (empty? violations))))

  (testing "Temperature below range -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-2" {:batch-temp-c -25.0
                           :product-type "frozen-peas"}}})
          request {:op :log-production-batch :subject "batch-2"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (seq violations))
      (is (= :batch-temp-out-of-range (-> violations first :rule)))))

  (testing "Temperature above range -> violation"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-3" {:batch-temp-c 40.0
                           :product-type "canned-tomato"}}})
          request {:op :log-production-batch :subject "batch-3"}
          violations (#'governor/batch-temp-out-of-range-violations request st)]
      (is (seq violations))
      (is (= :batch-temp-out-of-range (-> violations first :rule))))))

;; ──────────────────────── Storage Time ──────────────────────

(deftest storage-time-exceeded-violations-test
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

;; ──────────────────────── Spoilage Flag ──────────────────────

(deftest spoilage-flag-unresolved-violations-test
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

;; ──────────────────────── Composite check ──────────────────────

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

(deftest check-ok-verdict-test
  (testing "Routine maintenance scheduling, all checks clean -> ok? true"
    (let [st (store/mem-store {:initial-batches {"batch-1" clean-batch}})
          request {:op :schedule-maintenance :subject "equipment-1"}
          proposal {:value {} :cites ["Equipment-Manual"] :effect :propose :confidence 0.9}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (true? (:ok? verdict)))
      (is (empty? (:violations verdict)))
      (is (false? (:escalate? verdict))))))

(deftest check-hard-violations-test
  (testing "Hard violations -> ok? false, hard? true"
    (let [st (store/mem-store
              {:initial-batches
               {"batch-1" (assoc clean-batch
                                  :spoilage-flag-raised? true
                                  :spoilage-flag-resolved? false)}})
          request {:op :log-production-batch :subject "batch-1" :stake :log-production-batch}
          proposal {:value {:jurisdiction "US"} :cites ["FDA-FSMA"] :effect :propose :confidence 0.85}
          context {:actor-id "test"}
          verdict (governor/check request context proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:hard? verdict)))
      (is (seq (:violations verdict))))))

(deftest check-always-escalates-high-stakes-and-food-safety-flags-test
  (testing "log-production-batch escalates even when every check is clean"
    (let [st (store/mem-store {:initial-batches {"batch-1" clean-batch}})
          request {:op :log-production-batch :subject "batch-1"}
          proposal {:value {:jurisdiction "US"} :cites ["FDA-FSMA"] :effect :propose :confidence 0.95}
          verdict (governor/check request {:actor-id "test"} proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:escalate? verdict)))
      (is (false? (:hard? verdict)))
      (is (true? (:high-stakes? verdict)))))

  (testing "coordinate-shipment escalates even when every check is clean"
    (let [st (store/mem-store {:initial-batches {"batch-1" clean-batch}})
          request {:op :coordinate-shipment :subject "batch-1"}
          proposal {:value {:batch-id "batch-1"} :cites ["FDA-Sanitary-Transportation-Rule"] :effect :propose :confidence 0.95}
          verdict (governor/check request {:actor-id "test"} proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:escalate? verdict)))
      (is (false? (:hard? verdict)))))

  (testing "flag-food-safety-concern ALWAYS escalates, even at high confidence and no hard violations"
    (let [st (store/mem-store {:initial-batches {"batch-1" clean-batch}})
          request {:op :flag-food-safety-concern :subject "batch-1"}
          proposal {:value {:batch-id "batch-1"} :cites ["FDA-21-CFR-113-Scheduled-Process"]
                    :effect :propose :confidence 0.99}
          verdict (governor/check request {:actor-id "test"} proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:escalate? verdict)))
      (is (false? (:hard? verdict)))
      (is (false? (:high-stakes? verdict))))))

(deftest check-low-confidence-escalates-test
  (testing "Low confidence on a routine op escalates even when hard checks pass"
    (let [st (store/mem-store {:initial-batches {"batch-1" clean-batch}})
          request {:op :schedule-maintenance :subject "equipment-1"}
          proposal {:value {} :cites ["Equipment-Manual"] :effect :propose :confidence 0.3}
          verdict (governor/check request {:actor-id "test"} proposal st)]
      (is (false? (:ok? verdict)))
      (is (true? (:escalate? verdict)))
      (is (false? (:hard? verdict))))))

;; ──────────────────────── Double-Commit Prevention ──────────────────────

(deftest already-processed-check
  (testing "Batch already processed -> violation"
    (let [st (store/mem-store
              {:initial-batches {"batch-1" {:processed? true}}})
          request {:op :log-production-batch :subject "batch-1"}
          violations (#'governor/already-processed-violations request st)]
      (is (seq violations))
      (is (= :already-processed (-> violations first :rule))))))

(deftest already-shipment-finalized-check
  (testing "Batch shipment already finalized -> violation"
    (let [st (store/mem-store
              {:initial-batches {"batch-1" {:shipment-finalized? true}}})
          request {:op :coordinate-shipment :subject "batch-1"}
          violations (#'governor/already-shipment-finalized-violations request st)]
      (is (seq violations))
      (is (= :already-shipment-finalized (-> violations first :rule))))))
