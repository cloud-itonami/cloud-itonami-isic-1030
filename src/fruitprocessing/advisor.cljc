(ns fruitprocessing.advisor
  "FruitVegetableOpsAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes batch operations based on plant state and
  incoming fruit/vegetable lots. The advisor is SEALED into the `:advise`
  node of the operation graph; every proposal is routed through the
  independent Governor before committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (food-safety, compliance checks)
    2. Phase gate (rollout stage)
    3. Human operator (for high-stakes actions)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as sibling
  food-manufacturing coordination actors in the cloud-itonami actor
  family).")

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with
    :op, :stake, :effect, :value, :cites, :summary, :confidence"))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op subject]} request]
      (case op
        :log-production-batch
        {:op :log-production-batch
         :stake :log-production-batch
         :effect :propose
         :value {:jurisdiction "US"
                 :batch-id subject
                 :action "Log batch into production records"}
         :cites ["FDA-Food-Safety-Modernization-Act"]
         :summary "Harvest-lot provenance verified; sanitation and residue screening clear; ready for production logging"
         :confidence 0.85}

        :coordinate-shipment
        {:op :coordinate-shipment
         :stake :coordinate-shipment
         :effect :propose
         :value {:batch-id subject
                 :destination "Distribution center"
                 :transport-mode "refrigerated-truck"}
         :cites ["FDA-Sanitary-Transportation-Rule"]
         :summary "Finished product ready for shipment; storage temperature maintained throughout"
         :confidence 0.80}

        :flag-food-safety-concern
        {:op :flag-food-safety-concern
         :stake :monitoring
         :effect :propose
         :value {:batch-id subject
                 :concern-type "low-acid-canning-process-deviation"
                 :description "Retort scheduled-process record shows a temperature/time deviation from the filed process for this low-acid canned product -- botulism (C. botulinum) risk cannot be ruled out without process-authority review"
                 :recommended-action "hold-for-process-authority-review"}
         :cites ["FDA-21-CFR-113-Scheduled-Process"]
         :summary "Possible scheduled-process deviation on a low-acid canning batch; escalating for mandatory process-authority review -- this actor has no certification authority"
         :confidence 0.65}

        :schedule-maintenance
        {:op :schedule-maintenance
         :stake :operational
         :effect :propose
         :value {:equipment-id "MD-002"
                 :maintenance-type "metal-detector-calibration"
                 :proposed-date "2026-07-22"}
         :cites ["Equipment-Manual-Metal-Detector-2024"]
         :summary "Metal detector due for scheduled calibration"
         :confidence 0.90}

        ;; fallback
        {:op op
         :stake :unknown
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
