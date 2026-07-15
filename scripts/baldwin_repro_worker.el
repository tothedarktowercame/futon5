;;; baldwin_repro_worker.el --- Instrument legacy Baldwin runs -*- lexical-binding: t -*-

;; The vendored source is loaded unmodified. This side-file wraps only the
;; mutation function so requested counts and actual buffer write positions are
;; observed without changing the random draw sequence or mutation result.

(require 'cl-lib)

(defvar baldwin--original-mutation nil)
(defvar baldwin--requested-hist nil)
(defvar baldwin--written-hist nil)
(defvar baldwin--mutation-calls 0)
(defvar baldwin--positive-calls 0)
(defvar baldwin--changed-calls 0)
(defvar baldwin--mutation-steps 0)

(defun baldwin--inc (table key)
  (puthash key (1+ (gethash key table 0)) table))

(defun baldwin--reset-metrics ()
  (setq baldwin--requested-hist (make-hash-table :test #'eql)
        baldwin--written-hist (make-hash-table :test #'eql)
        baldwin--mutation-calls 0
        baldwin--positive-calls 0
        baldwin--changed-calls 0
        baldwin--mutation-steps 0))

(defun baldwin--instrumented-mutation (genotype n)
  (setq baldwin--mutation-calls (1+ baldwin--mutation-calls))
  (baldwin--inc baldwin--requested-hist n)
  (when (> n 0) (setq baldwin--positive-calls (1+ baldwin--positive-calls)))
  (let ((initial genotype)
        (current genotype)
        (original-goto (symbol-function 'goto-char)))
    ;; Invoke the original N-step function once.  In particular, do not split
    ;; it into one-step calls: the legacy loop reads from its original argument
    ;; on every iteration, so splitting would change repeated-write semantics.
    (cl-letf (((symbol-function 'goto-char)
               (lambda (position &optional buffer)
                 (ignore buffer)
                 (prog1 (funcall original-goto position)
                   ;; Record the zero-based cell at the actual post-clamp
                   ;; buffer point, not the requested random position.
                   (baldwin--inc baldwin--written-hist (1- (point)))))))
      (setq current (funcall baldwin--original-mutation genotype n)))
    (setq baldwin--mutation-steps
          (+ baldwin--mutation-steps (max 0 n)))
    (unless (string= initial current)
      (setq baldwin--changed-calls (1+ baldwin--changed-calls)))
    current))

(defun baldwin--install-instrumentation (version)
  (let ((symbol (if (string= version "2014")
                    'mutate-genotype-n
                  'mutate-rule-n)))
    (setq baldwin--original-mutation (symbol-function symbol))
    (fset symbol #'baldwin--instrumented-mutation)))

(defun baldwin--rule-row (sigils)
  (mapcar (lambda (c)
            (first (get-genotype-from-sigil (char-to-string c))))
          (string-to-list sigils)))

(defun baldwin--changed-cells (a b)
  (cl-count-if #'identity (cl-mapcar (lambda (x y) (not (eq x y)))
                                     (string-to-list a) (string-to-list b))))

(defun baldwin--hist-list (table keys)
  (mapcar (lambda (key) (list key (gethash key table 0))) keys))

(defun baldwin--run (version arm seed width steps)
  (defalias 'evolve-sigil-fn
    (if (string= arm "baldwin")
        'evolve-sigil-with-blending-baldwin
      'evolve-sigil-with-blending-mutation))
  (random (format "baldwin-repro-%d" seed))
  (baldwin--reset-metrics)
  (let* ((gen (random-sigil-string width))
         (phe (random-phenotype-string width))
         (gen-rows (list (baldwin--rule-row gen)))
         (phe-rows (list phe))
         (phe-activity nil)
         (gen-activity nil)
         (positive-by-generation nil)
         (calls-by-generation nil)
         (changed-calls-by-generation nil))
    (dotimes (_ steps)
      (let ((calls-before baldwin--mutation-calls)
            (positive-before baldwin--positive-calls)
            (changed-before baldwin--changed-calls)
            (old-gen gen)
            (old-phe phe)
            next)
        (setq next (co-evolve-phenotype-and-genotype gen phe)
              gen (first next)
              phe (second next))
        (push (baldwin--rule-row gen) gen-rows)
        (push phe phe-rows)
        (push (baldwin--changed-cells old-phe phe) phe-activity)
        (push (baldwin--changed-cells old-gen gen) gen-activity)
        (push (- baldwin--positive-calls positive-before) positive-by-generation)
        (push (- baldwin--mutation-calls calls-before) calls-by-generation)
        (push (- baldwin--changed-calls changed-before)
              changed-calls-by-generation)))
    (list :status :complete :version version :arm arm :seed seed
          :protocol (list :width width :steps steps :recorded-rows (1+ steps))
          :genotype (nreverse gen-rows)
          :phenotype (nreverse phe-rows)
          :phenotype-activity (nreverse phe-activity)
          :genotype-activity (nreverse gen-activity)
          :positive-mutation-calls-by-generation
          (nreverse positive-by-generation)
          :mutation-calls-by-generation (nreverse calls-by-generation)
          :changed-mutation-calls-by-generation
          (nreverse changed-calls-by-generation)
          :requested-mutation-hist
          (baldwin--hist-list baldwin--requested-hist '(-1 0 1 2 3 4 5))
          :written-position-hist
          (baldwin--hist-list baldwin--written-hist '(0 1 2 3 4 5 6 7))
          :mutation-calls baldwin--mutation-calls
          :positive-mutation-calls baldwin--positive-calls
          :changed-mutation-calls baldwin--changed-calls
          :mutation-steps baldwin--mutation-steps)))

(defun baldwin--write-atomic (path value)
  (let* ((dir (file-name-directory path))
         (tmp nil))
    (make-directory dir t)
    (setq tmp (make-temp-file (expand-file-name ".partial-" dir)))
    (unwind-protect
        (progn
          (with-temp-file tmp (prin1 value (current-buffer)) (insert "\n"))
          (rename-file tmp path t)
          (setq tmp nil))
      (when (and tmp (file-exists-p tmp)) (delete-file tmp)))))

(defun baldwin-repro-run-batch (version source task-file)
  "Load unedited SOURCE and atomically execute every task in TASK-FILE."
  (let ((vendor-dir (file-name-directory source)))
    (add-to-list 'load-path vendor-dir)
    (load source nil t))
  (baldwin--install-instrumentation version)
  (let ((tasks (with-temp-buffer
                 (insert-file-contents task-file)
                 (goto-char (point-min))
                 (read (current-buffer))))
        (done 0))
    (dolist (task tasks)
      (let ((result (baldwin--run version
                                  (plist-get task :arm)
                                  (plist-get task :seed)
                                  (plist-get task :width)
                                  (plist-get task :steps))))
        (baldwin--write-atomic (plist-get task :path) result)
        (setq done (1+ done))
        (princ (format "completed %s %s seed=%d (%d/%d)\n"
                       version (plist-get task :arm) (plist-get task :seed)
                       done (length tasks)))
        (redisplay)))
    (princ (format "batch complete: %s %d runs\n" version done))))

(provide 'baldwin-repro-worker)
;;; baldwin_repro_worker.el ends here
