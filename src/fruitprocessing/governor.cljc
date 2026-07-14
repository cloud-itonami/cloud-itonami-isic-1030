(ns fruitprocessing.governor
  "Fruit & Vegetable Processing Governor -- the independent compliance layer
  that earns the FruitVegetableOpsAdvisor the right to commit. The LLM has
  no notion of:
    - Whether a plant/batch record is independently verified/registered
    - Whether a batch's actual temperature lies inside its post-processing
      storage window
    - Whether storage time has exceeded allowable limits (spoilage/decay risk)
    - Whether plant sanitation meets jurisdiction requirements
    - Whether pesticide/residue screening passed
    - Whether an open contamination flag (spoilage, mold, bruising) resolved
    - Whether a batch's traceability checklist is complete per jurisdiction

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  Unlike direct processing-line control (NEVER done by this actor --
  blanching, canning/retort scheduling, freezing, and drying equipment
  operation remain exclusive to licensed plant operators / process
  authorities), the Governor operates on batch metadata: harvest origin,
  timing, sanitation records, residue/spoilage assessments, and
  food-safety flags. This is plant-operations coordination, not process
  control.

  CRITICAL: Any proposal involving food-safety concerns (spoilage, mold,
  residue risk, temperature breach, storage time, possible low-acid-canning
  scheduled-process deviation) ALWAYS escalates to human operator for final
  sign-off. The LLM's confidence is never sufficient for food-safety
  decisions, and this actor has no food-safety CERTIFICATION authority of
  its own -- see `op-not-allowed-violations`.

  Hard violations (always HOLD, no override):
    1. Operation outside the closed allowlist (e.g. direct processing-line
       control or a food-safety certification action)
    2. Proposal asserting an `:effect` other than `:propose`
    3. Plant/batch record not independently verified/registered in the store
    4. No jurisdiction citation (jurisdiction unknown -> can't verify reqs)
    5. Evidence checklist incomplete (missing required-evidence per jurisdiction)
    6. Batch temperature out of range (post-processing storage-window breach)
    7. Storage time exceeded (spoilage/decay risk)
    8. Sanitation score insufficient (plant hygiene not verified)
    9. Residue screening incomplete or failed
   10. Spoilage/contamination flag unresolved (open food-safety concern)
   11. Batch already processed / shipment already finalized (no double-commit)

  Soft gates (always escalate for human, even when the hard checks are
  clean):
    - Low confidence
    - Real actuation (`:log-production-batch`, `:coordinate-shipment`)
    - `:flag-food-safety-concern` -- a food-safety concern is never
      auto-resolved by advisor confidence alone, it always needs a human
      look, even when nothing else about the proposal is wrong

  This design mirrors sibling food-manufacturing coordination actors
  (dairy/bakery/meat processing) in the cloud-itonami actor family, but
  specializes food-safety concerns for produce (spoilage, pesticide/
  herbicide residue, low-acid-canning botulism risk, traceability) rather
  than pathogen/cold-chain or allergen concerns."
  (:require [fruitprocessing.facts :as facts]
            [fruitprocessing.registry :as registry]
            [fruitprocessing.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a batch into production records (`:log-production-batch`) and
  coordinating shipment of finished product (`:coordinate-shipment`) are the
  two real-world actuation events this actor performs. Both require plant
  operator sign-off."
  #{:log-production-batch :coordinate-shipment})

(def always-escalate-ops
  "Operations that always require human sign-off, even when the Governor's
  hard checks are clean and confidence is high: the two high-stakes
  actuation events (`high-stakes`) plus `:flag-food-safety-concern` -- a
  food-safety concern is never auto-resolved by advisor confidence alone,
  it always needs a human look."
  (conj high-stakes :flag-food-safety-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly direct
  processing-line control (blanching / canning / retort-schedule / freezing
  / drying) or food-safety CERTIFICATION authority -- is a hard, permanent
  block: this actor coordinates plant operations, it does not operate
  equipment and it does not certify food safety."
  #{:log-production-batch :schedule-maintenance :flag-food-safety-concern :coordinate-shipment})

;; ----------------------------- checks -----------------------------

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. direct processing-line/retort-schedule control, or a
  food-safety certification action) is refused unconditionally -- this
  actor has no authority to make such a proposal at all, let alone commit
  it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-production-batch/"
                  "schedule-maintenance/flag-food-safety-concern/coordinate-shipment) "
                  "に含まれない -- 加工ライン制御やfood-safety認証権限はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- batch-not-registered-violations
  "HARD invariant: a plant/batch record must be independently
  verified/registered in the store before ANY proposal referencing it
  (`:log-production-batch` / `:coordinate-shipment` /
  `:flag-food-safety-concern`) can proceed -- coordinating or flagging a
  batch this plant never checked in is out of scope for this actor."
  [{:keys [op subject]} st]
  (when (contains?
         #{:log-production-batch :coordinate-shipment :flag-food-safety-concern}
         op)
    (when-not (store/processing-batch st subject)
      [{:rule :batch-not-registered
        :detail (str subject " はプラントに登録されたバッチ記録が無い -- 提案は進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's food-safety requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-production-batch :coordinate-shipment :flag-food-safety-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式specificationの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-production-batch`, verify the batch's evidence checklist is
  complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/processing-batch st subject)]
      (when-not (and b
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b)
                      (:evidence-checklist b)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(harvest-lot-record/temperature-log/storage-time-record等)が充足していない状態での提案"}]))))

(defn- batch-temp-out-of-range-violations
  "For `:log-production-batch`, INDEPENDENTLY verify the batch's actual
  post-processing storage temperature stays inside its window [min,max] via
  `registry/batch-temp-out-of-range?`. Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/processing-batch st subject)
          p (when b (facts/product-type-by-id (:product-type b)))]
      (when (and b p (:batch-temp-c b)
                 (registry/batch-temp-out-of-range?
                  (:batch-temp-c b)
                  (:cold-chain-temp-min-c p)
                  (:cold-chain-temp-max-c p)))
        [{:rule :batch-temp-out-of-range
          :detail (str subject " の温度(" (:batch-temp-c b) " ℃)が保管窓["
                      (:cold-chain-temp-min-c p) ", "
                      (:cold-chain-temp-max-c p) "] ℃ の外 -- バッチ登録提案は進められない")}]))))

(defn- storage-time-exceeded-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the batch's
  storage time does not exceed the jurisdiction maximum via
  `registry/storage-time-exceeded?`. Evaluated UNCONDITIONALLY.
  Storage time determines spoilage/decay risk for produce."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/processing-batch st subject)
          j (when b (facts/jurisdiction-by-id (:jurisdiction b)))]
      (when (and b j (:storage-time-days b)
                 (registry/storage-time-exceeded?
                  (:storage-time-days b)
                  (:storage-time-max-days j)))
        [{:rule :storage-time-exceeded
          :detail (str subject " の保管時間(" (:storage-time-days b)
                      " 日)が法域限度(" (:storage-time-max-days j)
                      " 日)を超過 -- バッチ登録提案は進められない")}]))))

(defn- sanitation-score-insufficient-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the plant's
  sanitation score meets minimum requirements."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/processing-batch st subject)]
      (when (and b (:sanitation-score b)
                 (registry/sanitation-score-insufficient? (:sanitation-score b) 80))
        [{:rule :sanitation-score-insufficient
          :detail (str subject " のプラント衛生スコア(" (:sanitation-score b)
                      ")が最低要件(80)を下回る -- バッチ登録提案は進められない")}]))))

(defn- residue-screening-failed-violations
  "For `:log-production-batch`, INDEPENDENTLY verify the residue screening
  (pesticide, herbicide, other agrochemical) passed."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/processing-batch st subject)]
      (when (and b (not (registry/residue-screening-pass? (:residue-screening b))))
        [{:rule :residue-screening-failed
          :detail (str subject " が農薬等の残留スクリーニングを通過せず -- バッチ登録提案は進められない")}]))))

(defn- spoilage-flag-unresolved-violations
  "An unresolved spoilage/contamination flag is a HARD, un-overridable hold.
  Spoilage concerns (visible mold, bruising, rot, pest damage, suspected
  microbial contamination, temperature breach during receipt) raised during
  intake or processing MUST be resolved before the batch can be logged into
  production. Evaluated UNCONDITIONALLY at `:log-production-batch`."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/processing-batch st subject)]
      (when (and (true? (:spoilage-flag-raised? b))
                 (not (true? (:spoilage-flag-resolved? b))))
        [{:rule :spoilage-flag-unresolved
          :detail (str subject " は未解決の腐敗フラグがある -- バッチ登録提案は進められない")}]))))

(defn- already-processed-violations
  "For `:log-production-batch`, refuse to process the SAME batch twice, off
  a dedicated `:processed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (when (store/batch-already-processed? st subject)
      [{:rule :already-processed
        :detail (str subject " は既に登録済み")}])))

(defn- already-shipment-finalized-violations
  "For `:coordinate-shipment`, refuse to finalize the SAME batch's shipment
  twice, off a dedicated `:shipment-finalized?` fact."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when (store/batch-shipment-finalized? st subject)
      [{:rule :already-shipment-finalized
        :detail (str subject " は既に出荷確定済み")}])))

(defn check
  "Censors a FruitVegetableOpsAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate) are read off the
  REQUEST's `:op` -- not off the proposal's self-reported `:stake` -- since
  the operation being proposed (not the advisor's self-reported stake) is
  what determines whether a human must sign off. This is what guarantees
  `:flag-food-safety-concern` always escalates even if an advisor were to
  mislabel its own stake."
  [request _context proposal st]
  (let [hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (batch-not-registered-violations request st)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (batch-temp-out-of-range-violations request st)
                           (storage-time-exceeded-violations request st)
                           (sanitation-score-insufficient-violations request st)
                           (residue-screening-failed-violations request st)
                           (spoilage-flag-unresolved-violations request st)
                           (already-processed-violations request st)
                           (already-shipment-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (boolean (always-escalate-ops (:op request)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
