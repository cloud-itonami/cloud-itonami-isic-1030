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
