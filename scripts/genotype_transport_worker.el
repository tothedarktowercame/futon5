;;; genotype_transport_worker.el --- Genotype transport batch adapter -*- lexical-binding: t -*-

;; This adapter contains no dynamics. `run-propagator' in elisp-harness/run.el
;; executes the unedited vendored MetaCA source.  Here we only expose each
;; genotype sigil as its canonical eight-bit rule string and persist the run.

(require 'cl-lib)

(defun genotype-transport--vector-edn (xs)
  (format "[%s]" (mapconcat #'number-to-string (append xs nil) " ")))

(defun genotype-transport--rule-row-edn (row)
  (format "[%s]"
          (mapconcat
           (lambda (c)
             (prin1-to-string
              (first (get-genotype-from-sigil (char-to-string c)))))
           (string-to-list row) " ")))

(defun genotype-transport--grid-edn (rows)
  (format "[%s]"
          (mapconcat #'genotype-transport--rule-row-edn rows "\n")))

(defun genotype-transport--write-result (task result)
  (let* ((path (plist-get task :path))
         (dir (file-name-directory path))
         (tmp nil))
    (make-directory dir t)
    (setq tmp (make-temp-file (expand-file-name ".partial-" dir)))
    (unwind-protect
        (progn
          (with-temp-file tmp
            (insert
             (format (concat "{:status :complete :fingerprint %S "
                             ":label %S :perm %s :seed %d "
                             ":protocol {:width %d :steps %d :invert true} "
                             ":measured {:death %d :rules %d :activity %d "
                             ":genotype %s}}\n")
                     (plist-get task :fingerprint)
                     (plist-get task :label)
                     (genotype-transport--vector-edn (plist-get task :perm))
                     (plist-get task :seed)
                     (plist-get task :width)
                     (plist-get task :steps)
                     (plist-get result :death)
                     (plist-get result :rules)
                     (plist-get result :activity)
                     (genotype-transport--grid-edn
                      (plist-get result :gen)))))
          (rename-file tmp path t)
          (setq tmp nil))
      (when (and tmp (file-exists-p tmp)) (delete-file tmp)))))

(defun genotype-transport-run-batch (task-file)
  "Run TASK-FILE through the authoritative harness and persist each result."
  (let ((tasks (with-temp-buffer
                 (insert-file-contents task-file)
                 (goto-char (point-min))
                 (read (current-buffer))))
        (done 0))
    (dolist (task tasks)
      (let ((result (run-propagator (plist-get task :perm)
                                    (plist-get task :seed)
                                    (plist-get task :width)
                                    (plist-get task :steps))))
        (genotype-transport--write-result task result)
        (setq done (1+ done))
        (princ (format "completed %d/%d\n" done (length tasks)))))
    (princ (format "batch complete: %d runs\n" done))))

(provide 'genotype-transport-worker)
;;; genotype_transport_worker.el ends here
