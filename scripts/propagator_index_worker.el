;;; -*- lexical-binding: t -*-
;;; propagator_index_worker.el --- Composition-census worker for S8 propagators

;; Load after scripts/elisp-harness/run.el.  The original 2014 engine remains
;; the sole dynamics authority; this file decodes its genotype rows into dense
;; standard-Wolfram ECA censuses and writes atomic, compressed artefacts.

(require 'cl-lib)

(defconst propagator-index--legacy-to-standard [0 1 2 4 3 5 6 7])
(defconst propagator-index--class-4
  '(110 124 137 193 54 147 106 120 169 225 41 97))

(defvar propagator-index--sigil-to-standard nil)

(defun propagator-index--legacy-rule-to-standard (rule)
  (let ((n 0))
    (dotimes (legacy 8 n)
      (when (= (aref rule legacy) ?1)
        (setq n (+ n (ash 1 (aref propagator-index--legacy-to-standard
                                   legacy))))))))

(defun propagator-index--init-sigil-table ()
  (setq propagator-index--sigil-to-standard (make-hash-table :test #'eql))
  (dolist (entry truth-table-8)
    (puthash (string-to-char (second entry))
             (propagator-index--legacy-rule-to-standard (first entry))
             propagator-index--sigil-to-standard))
  (unless (and (= 256 (hash-table-count propagator-index--sigil-to-standard))
               (= 110 (propagator-index--legacy-rule-to-standard "01101110")))
    (error "legacy-to-standard Wolfram rule map failed its Rule-110 anchor")))

(defun propagator-index--census-row (row)
  (let ((counts (make-vector 256 0)))
    (dolist (sigil (string-to-list row))
      (let ((rule (gethash sigil propagator-index--sigil-to-standard)))
        (unless rule (error "unknown genotype sigil: %S" sigil))
        (aset counts rule (1+ (aref counts rule)))))
    counts))

(defun propagator-index--class-4-count (census)
  (apply #'+ (mapcar (lambda (rule) (aref census rule))
                     propagator-index--class-4)))

(defun propagator-index--vector-edn (xs)
  (format "[%s]" (mapconcat #'number-to-string (append xs nil) " ")))

(defun propagator-index--census-edn (rows)
  (format "[%s]"
          (mapconcat #'propagator-index--vector-edn rows "\n")))

(defun propagator-index--seed-result (perm seed width steps)
  (let* ((run (run-propagator perm seed width steps))
         (census (mapcar #'propagator-index--census-row
                         (plist-get run :gen)))
         (class-4 (mapcar #'propagator-index--class-4-count census))
         (terminal-distinct
          (cl-count-if (lambda (n) (> n 0)) (car (last census)))))
    (unless (= (length census) (1+ steps))
      (error "census horizon mismatch: %d" (length census)))
    (unless (cl-every (lambda (row) (= width (apply #'+ (append row nil))))
                      census)
      (error "census row does not sum to width"))
    (unless (= terminal-distinct (plist-get run :rules))
      (error "terminal census/rule-count mismatch"))
    (list :seed seed
          :death (plist-get run :death)
          :rules (plist-get run :rules)
          :activity (plist-get run :activity)
          :class-4 class-4
          :census census)))

(defun propagator-index--seed-edn (seed-result)
  (format (concat "{:seed %d :death %d :rules %d :activity %d "
                  ":class-4 %s :census %s}")
          (plist-get seed-result :seed)
          (plist-get seed-result :death)
          (plist-get seed-result :rules)
          (plist-get seed-result :activity)
          (propagator-index--vector-edn (plist-get seed-result :class-4))
          (propagator-index--census-edn (plist-get seed-result :census))))

(defun propagator-index--sha256-file (path)
  (with-temp-buffer
    (set-buffer-multibyte nil)
    (insert-file-contents-literally path)
    (secure-hash 'sha256 (current-buffer))))

(defun propagator-index--atomic-gzip (contents path)
  (let* ((dir (file-name-directory path))
         (raw (make-temp-file (expand-file-name ".partial-" dir)))
         (gz (concat raw ".gz")))
    (unwind-protect
        (progn
          (with-temp-file raw (insert contents))
          (unless (= 0 (call-process "gzip" nil nil nil "-n" "-f" raw))
            (error "gzip failed for %s" raw))
          (rename-file gz path t)
          (setq gz nil raw nil))
      (when (and raw (file-exists-p raw)) (delete-file raw))
      (when (and gz (file-exists-p gz)) (delete-file gz)))))

(defun propagator-index--atomic-text (contents path)
  (let* ((dir (file-name-directory path))
         (tmp (make-temp-file (expand-file-name ".partial-" dir))))
    (unwind-protect
        (progn (with-temp-file tmp (insert contents))
               (rename-file tmp path t)
               (setq tmp nil))
      (when (and tmp (file-exists-p tmp)) (delete-file tmp)))))

(defun propagator-index--write-task (task)
  (let* ((perm (plist-get task :perm))
         (fingerprint (plist-get task :fingerprint))
         (width (plist-get task :width))
         (steps (plist-get task :steps))
         (seeds (append (plist-get task :seeds) nil))
         (artifact (plist-get task :artifact))
         (manifest (plist-get task :manifest))
         (results (mapcar (lambda (seed)
                            (propagator-index--seed-result
                             perm seed width steps)) seeds))
         (artifact-edn
          (format (concat "{:status :complete :fingerprint %S :sigma %s "
                          ":protocol {:width %d :steps %d :seeds %s "
                          ":invert true :census-shape [3 %d 256] "
                          ":rule-numbering :standard-wolfram} "
                          ":legacy-to-standard %s :class-4-rules %s "
                          ":runs [%s]}\n")
                  fingerprint
                  (propagator-index--vector-edn perm)
                  width steps (propagator-index--vector-edn seeds)
                  (1+ steps)
                  (propagator-index--vector-edn
                   propagator-index--legacy-to-standard)
                  (propagator-index--vector-edn propagator-index--class-4)
                  (mapconcat #'propagator-index--seed-edn results "\n"))))
    (make-directory (file-name-directory artifact) t)
    (make-directory (file-name-directory manifest) t)
    (propagator-index--atomic-gzip artifact-edn artifact)
    (let ((sha (propagator-index--sha256-file artifact))
          (bytes (file-attribute-size (file-attributes artifact))))
      (propagator-index--atomic-text
       (format (concat "{:status :complete :fingerprint %S :sigma %s "
                       ":artifact-sha256 %S :artifact-bytes %d "
                       ":runs [%s]}\n")
               fingerprint (propagator-index--vector-edn perm) sha bytes
               (mapconcat
                (lambda (r)
                  (format (concat "{:seed %d :death %d :rules %d :activity %d "
                                  ":class-4-terminal %d}")
                          (plist-get r :seed) (plist-get r :death)
                          (plist-get r :rules) (plist-get r :activity)
                          (car (last (plist-get r :class-4)))))
                results " "))
       manifest))))

(defun propagator-index-run-batch (task-file)
  (propagator-index--init-sigil-table)
  (let ((tasks (with-temp-buffer
                 (insert-file-contents task-file)
                 (goto-char (point-min))
                 (read (current-buffer))))
        (done 0))
    (dolist (task tasks)
      (propagator-index--write-task task)
      (setq done (1+ done))
      (when (= 0 (% done 5))
        (princ (format "worker %s completed %d/%d\n"
                       (or (getenv "PROPAGATOR_WORKER_ID") "?")
                       done (length tasks)))))
    (princ (format "worker %s completed %d/%d\n"
                   (or (getenv "PROPAGATOR_WORKER_ID") "?")
                   done (length tasks)))))

(provide 'propagator-index-worker)
;;; propagator_index_worker.el ends here
