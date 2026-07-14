(ns fruitprocessing.store
  "Store abstraction for fruit & vegetable processing batches. Current
  implementation is an in-memory map; production should migrate to
  Datomic/kotoba-server (the same seam point all cloud-itonami actors use).

  A processing batch is the minimal unit of work: one delivery of fresh
  produce from a grower/supplier, tracked through intake, sanitation
  verification, processing (canning/freezing/drying) into a finished
  product, and shipment. Each batch has:
    - :id unique batch identifier (UUID / lot code)
    - :jurisdiction country code (US/JP/EU/etc)
    - :product-type finished-product id (see `fruitprocessing.facts/product-types`)
    - :received-at timestamp when the raw produce arrived
    - :batch-temp-c actual measured post-processing storage temperature
    - :storage-time-days cumulative days in storage since processing completed
    - :harvest-lot-verified? true once the batch's harvest-lot/grower
      provenance documentation cleared
    - :sanitation-score 0-100, third-party audit within 7 days
    - :residue-screening {:passed? :test-date-valid? :lab-accredited?}
      pesticide/herbicide/other-agrochemical MRL screening result
    - :spoilage-flag-raised? true if a spoilage/contamination concern
      (mold, bruising, rot, pest damage, suspected microbial contamination)
      surfaced during intake or processing
    - :spoilage-flag-resolved? true only if that concern is verified cleared
    - :evidence-checklist evidence items present for the batch
    - :processed? true once a `:log-production-batch` proposal committed
    - :shipment-finalized? true once a `:coordinate-shipment` proposal committed"
  (:require [clojure.set]))

;; Protocol for swappable store implementations
(defprotocol Store
  (processing-batch [store batch-id] "Retrieve a batch by ID")
  (batch-quality-of [store batch-id] "Retrieve quality/provenance summary for a batch")
  (batch-already-processed? [store batch-id] "Verify batch has not been processed twice")
  (batch-shipment-finalized? [store batch-id] "Verify batch shipment not finalized twice"))

;; In-memory implementation (MemStore) for development/testing
(defrecord MemStore [batches]
  Store
  (processing-batch [_store batch-id]
    (get @batches batch-id))

  (batch-quality-of [_store batch-id]
    (let [b (get @batches batch-id)]
      (when b
        {:harvest-lot-verified? (:harvest-lot-verified? b)
         :storage-time-days (:storage-time-days b)
         :sanitation-score (:sanitation-score b)
         :residue-screening (:residue-screening b)
         :checklist (:evidence-checklist b)})))

  (batch-already-processed? [_store batch-id]
    (let [b (get @batches batch-id)]
      (true? (:processed? b))))

  (batch-shipment-finalized? [_store batch-id]
    (let [b (get @batches batch-id)]
      (true? (:shipment-finalized? b)))))

(defn mem-store
  "Create an in-memory store. `initial-batches` is an optional map of
  batch-id -> batch-record."
  [& [{:keys [initial-batches] :or {initial-batches {}}}]]
  (MemStore. (atom initial-batches)))

(defn add-batch
  "Add or update a batch in the store. Used by tests and simulation."
  [^MemStore store batch-id batch-data]
  (swap! (:batches store) assoc batch-id batch-data)
  batch-data)

(defn mark-processed
  "Mark a batch as processed (one-way flag). Used by Governor to prevent
  double-processing of the same batch."
  [^MemStore store batch-id]
  (swap! (:batches store)
         (fn [batches]
           (if (contains? batches batch-id)
             (assoc-in batches [batch-id :processed?] true)
             batches))))

(defn mark-shipment-finalized
  "Mark a batch's shipment as finalized (one-way flag). Used by Governor to
  prevent double-shipment of the same batch."
  [^MemStore store batch-id]
  (swap! (:batches store)
         (fn [batches]
           (if (contains? batches batch-id)
             (assoc-in batches [batch-id :shipment-finalized?] true)
             batches))))
