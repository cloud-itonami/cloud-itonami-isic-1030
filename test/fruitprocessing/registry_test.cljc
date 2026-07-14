(ns fruitprocessing.registry-test
  (:require [clojure.test :refer [deftest is are testing]]
            [fruitprocessing.registry :as registry]))

(deftest batch-temp-out-of-range-test
  (testing "Temperature within range"
    (is (false? (registry/batch-temp-out-of-range? 3.5 -1.0 4.0))))

  (testing "Temperature at lower bound"
    (is (false? (registry/batch-temp-out-of-range? -1.0 -1.0 4.0))))

  (testing "Temperature at upper bound"
    (is (false? (registry/batch-temp-out-of-range? 4.0 -1.0 4.0))))

  (testing "Temperature below lower bound"
    (is (true? (registry/batch-temp-out-of-range? -1.5 -1.0 4.0))))

  (testing "Temperature above upper bound"
    (is (true? (registry/batch-temp-out-of-range? 5.0 -1.0 4.0)))))

(deftest storage-time-exceeded-test
  (testing "Storage time within limit"
    (is (false? (registry/storage-time-exceeded? 3 7))))

  (testing "Storage time at limit"
    (is (false? (registry/storage-time-exceeded? 7 7))))

  (testing "Storage time exceeds limit"
    (is (true? (registry/storage-time-exceeded? 8 7)))))

(deftest sanitation-score-insufficient-test
  (testing "Score meets minimum"
    (is (false? (registry/sanitation-score-insufficient? 85 80))))

  (testing "Score at minimum"
    (is (false? (registry/sanitation-score-insufficient? 80 80))))

  (testing "Score below minimum"
    (is (true? (registry/sanitation-score-insufficient? 79 80)))))

(deftest residue-screening-pass-test
  (testing "Passed with valid test date and accredited lab"
    (is (true? (registry/residue-screening-pass? {:passed? true :test-date-valid? true :lab-accredited? true}))))

  (testing "Failed screening"
    (is (false? (registry/residue-screening-pass? {:passed? false :test-date-valid? true :lab-accredited? true}))))

  (testing "Test date invalid"
    (is (false? (registry/residue-screening-pass? {:passed? true :test-date-valid? false :lab-accredited? true}))))

  (testing "Lab not accredited"
    (is (false? (registry/residue-screening-pass? {:passed? true :test-date-valid? true :lab-accredited? false})))))

(deftest spoilage-time-excessive-test
  (testing "Flag hold within safe window"
    (is (false? (registry/spoilage-time-excessive? 12.0))))

  (testing "Flag hold at boundary"
    (is (false? (registry/spoilage-time-excessive? 24.0))))

  (testing "Flag hold exceeds safe window"
    (is (true? (registry/spoilage-time-excessive? 25.0)))))
