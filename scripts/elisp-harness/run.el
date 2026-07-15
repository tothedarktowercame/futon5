;;; -*- lexical-binding: t -*- -- REQUIRED: see vendor/metaca/README.md
;;; Load the ORIGINAL 2014 MetaCA elisp and expose the propagator seam.
(let ((here (file-name-directory (or load-file-name buffer-file-name))))
  (add-to-list 'load-path here)
  (add-to-list 'load-path (expand-file-name "../../vendor/metaca" here)))
(require 'cl-lib)
(require 'clcompat)
(load (expand-file-name "vendor/metaca/256ca-2014-12-29-BUGGY.el"
                        (locate-dominating-file default-directory "deps.edn")) nil t)

;; Figure 8 = the blending dynamic + the erroneous mutation.
(defalias 'evolve-sigil-fn 'evolve-sigil-with-blending-mutation)

;; THE PROPAGATOR SEAM. The 2014 bug is one instance of:
;;     pick k at random; bit[sigma(k)] := NOT bit[k]
;; sigma = identity is ordinary mutation (a random walk, no attractor). A
;; non-trivial sigma COUPLES the bit-planes and gives the operator a fixed point,
;; which selects the rule the landscape lands on.
(defun bitref (g k) (substring g (mod k 8) (1+ (mod k 8))))
(defun bitset (g k v) (let ((k (mod k 8))) (concat (substring g 0 k) v (substring g (1+ k)))))
(defun binv (b) (if (string= b "0") "1" "0"))

(defun make-perm-prop (perm &optional no-invert)
  "PERM is an 8-vector: bit k writes to (aref PERM k)."
  (lambda (g _)
    (let ((p (random 8)))
      (bitset g (aref perm p) (if no-invert (bitref g p) (binv (bitref g p)))))))

(defun install-prop! (perm &optional no-invert)
  (fset 'mutate-genotype-n (make-perm-prop perm no-invert)))

;; --- measures that work (see M-propagators.md sec.2 for the ones that don't) ---
(defun phe-chg (a b)
  (let ((n 0)) (cl-mapc (lambda (x y) (unless (eq x y) (setq n (1+ n))))
                        (string-to-list a) (string-to-list b)) n))
(defun n-rules (row)
  (let (rs) (dolist (c (map 'list #'char-to-string (string-to-list row)))
              (cl-pushnew (first (get-genotype-from-sigil c)) rs :test #'string=)) (length rs)))

(defun run-propagator (perm seed width steps &optional no-invert)
  "Run the authoritative MetaCA propagator seam.

Return (:death t :rules n :activity n :phe rows :gen rows).  PHE contains
binary phenotype strings and GEN contains the corresponding genotype sigil
strings, including both initial rows.  Death is the last phenotype time with
any change."
  (install-prop! perm no-invert)
  (random (format "prop-%d" seed))
  (let* ((g (random-sigil-string width))
         (p (random-phenotype-string width))
         (ps (list p))
         (gs (list g)))
    (dotimes (_ steps)
      (setq p (evolve-phenotype-against-genotype g p))
      (setq g (evolve-sigil-string g))
      (push p ps)
      (push g gs))
    (setq ps (nreverse ps))
    (setq gs (nreverse gs))
    (let* ((acts (cl-loop for k from 1 below (length ps) collect (phe-chg (nth (1- k) ps) (nth k ps))))
           (death (let ((x 0)) (cl-loop for k from 0 below (length acts)
                                        do (when (> (nth k acts) 0) (setq x (1+ k)))) x)))
      (list :death death :rules (n-rules g) :activity (apply #'+ acts)
            :phe ps :gen gs))))
(provide 'run)
