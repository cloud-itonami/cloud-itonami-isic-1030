(ns fruitprocessing.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [fruitprocessing.facts :as facts]))

(deftest jurisdiction-lookup
  (testing "Lookup valid jurisdiction"
    (let [j (facts/jurisdiction-by-id "US")]
      (is (= "US" (:id j)))
      (is (= "United States (FDA/FSMA)" (:name j)))
      (is (= 7 (:storage-time-max-days j)))))

  (testing "Lookup invalid jurisdiction"
    (let [j (facts/jurisdiction-by-id "XX")]
      (is (nil? j)))))

(deftest required-evidence-satisfied
  (testing "All required evidence present"
    (let [checklist [:harvest-lot-record :temperature-log :storage-time-record
                     :sanitation-log :residue-screening-pass :traceability-record]
          satisfied (facts/required-evidence-satisfied? "US" checklist)]
      (is (true? satisfied))))

  (testing "Missing required evidence"
    (let [checklist [:harvest-lot-record :temperature-log]
          satisfied (facts/required-evidence-satisfied? "US" checklist)]
      (is (false? satisfied))))

  (testing "Extra evidence beyond requirements"
    (let [checklist [:harvest-lot-record :temperature-log :storage-time-record
                     :sanitation-log :residue-screening-pass :traceability-record
                     :low-acid-canning-process-record]
          satisfied (facts/required-evidence-satisfied? "US" checklist)]
      (is (true? satisfied))))

  (testing "EU requires the low-acid-canning scheduled-process record"
    (let [checklist [:harvest-lot-record :temperature-log :storage-time-record
                     :sanitation-log :residue-screening-pass :traceability-record]
          satisfied (facts/required-evidence-satisfied? "EU" checklist)]
      (is (false? satisfied))))

  (testing "Unknown jurisdiction"
    (let [checklist [:harvest-lot-record :temperature-log]
          satisfied (facts/required-evidence-satisfied? "XX" checklist)]
      (is (false? satisfied)))))

(deftest product-type-lookup
  (testing "Lookup valid product types"
    (are [id expected-name] (= expected-name (:name (facts/product-type-by-id id)))
      "canned-tomato" "トマト缶詰"
      "canned-green-beans" "グリーンビーンズ缶詰"
      "frozen-peas" "冷凍グリーンピース"
      "frozen-corn" "冷凍コーン"
      "dried-apricot" "ドライアプリコット"))

  (testing "Lookup invalid product type"
    (let [p (facts/product-type-by-id "unknown")]
      (is (nil? p)))))

(deftest product-type-storage-temp-specs
  (testing "Canned tomato specs (ambient, shelf-stable)"
    (let [p (facts/product-type-by-id "canned-tomato")]
      (is (= 0.0 (:cold-chain-temp-min-c p)))
      (is (= 35.0 (:cold-chain-temp-max-c p)))))

  (testing "Frozen peas specs (freezer window)"
    (let [p (facts/product-type-by-id "frozen-peas")]
      (is (= -22.0 (:cold-chain-temp-min-c p)))
      (is (= -18.0 (:cold-chain-temp-max-c p)))))

  (testing "Dried apricot specs (cool dry storage)"
    (let [p (facts/product-type-by-id "dried-apricot")]
      (is (= 5.0 (:cold-chain-temp-min-c p)))
      (is (= 25.0 (:cold-chain-temp-max-c p))))))

;; ─────── Downstream Cross-Actor Handoff (optional, isic-1030 -> isic-1075) ───────

(def ^:private well-formed-handoff
  {:handoff/id "h-1"
   :handoff/source-actor "cloud-itonami-isic-1030"
   :handoff/batch-id "batch-1"
   :handoff/product-type-id "frozen-peas"
   :handoff/quantity-kg 500.0
   :handoff/dispatched-at-iso "2026-07-17T00:00:00Z"})

(deftest handoff-record-well-formed-test
  (testing "complete handoff passes"
    (is (true? (facts/handoff-record-well-formed? well-formed-handoff))))

  (testing "missing :handoff/quantity-kg fails"
    (is (false? (facts/handoff-record-well-formed? (dissoc well-formed-handoff :handoff/quantity-kg)))))

  (testing "non-positive quantity fails"
    (is (false? (facts/handoff-record-well-formed? (assoc well-formed-handoff :handoff/quantity-kg 0)))))

  (testing "nil handoff fails"
    (is (false? (facts/handoff-record-well-formed? nil)))))
