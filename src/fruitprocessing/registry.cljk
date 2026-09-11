(ns fruitprocessing.registry
  "Pure validation functions for fruit & vegetable processing parameters. These
  are called by the Governor to independently verify physical/operational
  constraints -- the LLM advisor's confidence is NOT sufficient to override
  these checks.")

(defn batch-temp-out-of-range?
  "Independently verify that the batch's actual temperature stays inside
  the declared cold-chain window [min,max]. Both bounds are inclusive.
  The aerospace two-sided-tolerance discipline: both lower (thermal abuse)
  and upper (condensation/mold risk) bounds are HARD limits."
  [actual-temp-c min-temp-c max-temp-c]
  (or (< actual-temp-c min-temp-c)
      (> actual-temp-c max-temp-c)))

(defn storage-time-exceeded?
  "Independently verify that the batch's actual storage time in cold chain
  does not exceed the jurisdiction's maximum storage-time. Time zero is when
  the batch arrives at the processing plant; storage time includes transit.
  Exceed of storage time increases spoilage/decay risk."
  [actual-days-stored max-days-allowed]
  (> actual-days-stored max-days-allowed))

(defn sanitation-score-insufficient?
  "Independently verify that the plant's pre-processing sanitation score
  meets the minimum required by jurisdiction. Score is 0-100, assessed by
  a third-party auditor against FDA/EFSA/MHLW sanitation standards within
  7 days prior to batch processing."
  [actual-score min-score-required]
  (< actual-score min-score-required))

(defn residue-screening-pass?
  "Residue screening (pesticide, herbicide, fungicide, other agrochemicals)
  is a required check before batch processing:
  1. All product must pass the threshold per jurisdiction (typically MRL limits)
  2. Test date must be within 7 days of batch receipt
  3. Laboratory accreditation must be verified
  Returns true only if all are satisfied."
  [{:keys [passed? test-date-valid? lab-accredited?]}]
  (and (true? passed?)
       (true? test-date-valid?)
       (true? lab-accredited?)))

(defn spoilage-time-excessive?
  "For a batch with spoilage concern raised, verify that the batch has
  not deteriorated beyond a safe window after spoilage was flagged.
  If a concern is raised and more than 24 hours pass without being resolved,
  the batch cannot be processed (high risk of mold/decay/pathogen multiplication)."
  [hours-since-flagged]
  (> hours-since-flagged 24.0))
