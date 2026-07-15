;;; -*- lexical-binding: t -*-
;;; propagator_orbit_probe.el --- Prove usable symmetries of the propagator substrate

;; Load after scripts/elisp-harness/run.el.  This does not implement dynamics:
;; it supplies explicit initial conditions and mutation choices to the original
;; 2014 evolution functions so symmetry claims can be checked pathwise.

(require 'cl-lib)

(defconst propagator-orbit--mirror [0 3 2 1 6 5 4 7]
  "Legacy-index action of lcr -> rcl.")

(defconst propagator-orbit--complement [7 6 5 4 3 2 1 0]
  "Legacy-index action of bitwise neighbourhood complementation.")

(defun propagator-orbit--conjugate (perm symmetry)
  (apply #'vector
         (cl-loop for k from 0 below 8
                  collect (aref symmetry
                                (aref perm (aref symmetry k))))))

(defun propagator-orbit--rule-transform (rule symmetry &optional invert-output)
  (apply #'concat
         (cl-loop for k from 0 below 8
                  for bit = (substring rule (aref symmetry k)
                                       (1+ (aref symmetry k)))
                  collect (if invert-output (binv bit) bit))))

(defun propagator-orbit--sigil-transform (sig symmetry &optional invert-output)
  (get-sigil-from-rule
   (propagator-orbit--rule-transform
    (get-rule-from-sigil sig) symmetry invert-output)))

(defun propagator-orbit--gen-transform (row symmetry reverse-space &optional invert-output)
  (let ((chars (mapcar #'char-to-string (string-to-list row))))
    (when reverse-space (setq chars (reverse chars)))
    (apply #'concat
           (mapcar (lambda (sig)
                     (propagator-orbit--sigil-transform
                      sig symmetry invert-output))
                   chars))))

(defun propagator-orbit--phe-transform (row reverse-space &optional invert-bits)
  (let ((chars (mapcar #'char-to-string (string-to-list row))))
    (when reverse-space (setq chars (reverse chars)))
    (apply #'concat
           (if invert-bits (mapcar #'binv chars) chars))))

(defun propagator-orbit--make-scripted-prop (perm choices)
  (let ((remaining (copy-sequence choices)))
    (lambda (g _)
      (unless remaining (error "scripted mutation choices exhausted"))
      (let ((p (pop remaining)))
        (bitset g (aref perm p) (binv (bitref g p)))))))

(defun propagator-orbit--choices-transform (choices width symmetry reverse-space)
  "Transform mutation choices, including legacy head/tail call order.

`evolve-sigil-string' evaluates head, then tail, then left-to-right interior.
Under spatial reflection the corresponding call sequence is therefore tail,
head, then right-to-left interior; merely mapping bit indices is insufficient."
  (apply #'append
         (cl-loop for chunk on choices by (lambda (xs) (nthcdr width xs))
                  for row = (cl-subseq chunk 0 width)
                  for ordered = (if reverse-space
                                    (append (list (nth 1 row) (nth 0 row))
                                            (reverse (nthcdr 2 row)))
                                  row)
                  collect (mapcar (lambda (k) (aref symmetry k)) ordered))))

(defun propagator-orbit--run-explicit (perm genotype phenotype choices steps)
  (fset 'mutate-genotype-n
        (propagator-orbit--make-scripted-prop perm choices))
  (let ((g genotype) (p phenotype) (gs (list genotype)) (ps (list phenotype)))
    (dotimes (_ steps)
      (setq p (evolve-phenotype-against-genotype g p))
      (setq g (evolve-sigil-string g))
      (push g gs)
      (push p ps))
    (list :gen (nreverse gs) :phe (nreverse ps))))

(defun propagator-orbit--first-mismatch (expected actual)
  (cl-loop for x in expected for y in actual for idx from 0
           unless (string= x y) return idx))

(defun propagator-orbit--one-check (perm seed symmetry kind)
  (let* ((width 24) (steps 24)
         (_seed (random (format "orbit-%d" seed)))
         (g (random-sigil-string width))
         (p (random-phenotype-string width))
         (choices (cl-loop repeat (* width steps) collect (random 8)))
         (reverse-space (eq kind :mirror))
         (invert (eq kind :complement))
         (image (propagator-orbit--conjugate perm symmetry))
         (g-image (propagator-orbit--gen-transform
                   g symmetry reverse-space invert))
         (p-image (propagator-orbit--phe-transform
                   p reverse-space invert))
         (choices-image (propagator-orbit--choices-transform
                         choices width symmetry reverse-space))
         (a (propagator-orbit--run-explicit perm g p choices steps))
         (b (propagator-orbit--run-explicit
             image g-image p-image choices-image steps))
         (expected-gen
          (mapcar (lambda (row)
                    (propagator-orbit--gen-transform
                     row symmetry reverse-space invert))
                  (plist-get a :gen)))
         (expected-phe
          (mapcar (lambda (row)
                    (propagator-orbit--phe-transform row reverse-space invert))
                  (plist-get a :phe)))
         (gen-mismatch (propagator-orbit--first-mismatch
                        expected-gen (plist-get b :gen)))
         (phe-mismatch (propagator-orbit--first-mismatch
                        expected-phe (plist-get b :phe))))
    (list :sigma perm :image image :seed seed
          :gen-pathwise (null gen-mismatch)
          :phe-pathwise (null phe-mismatch)
          :first-gen-mismatch gen-mismatch
          :first-phe-mismatch phe-mismatch)))

(defun propagator-orbit--vector-edn (xs)
  (format "[%s]" (mapconcat #'number-to-string (append xs nil) " ")))

(defun propagator-orbit--value-edn (x)
  (cond ((vectorp x) (propagator-orbit--vector-edn x))
        ((eq x t) "true") ((eq x nil) "nil")
        ((keywordp x) (symbol-name x))
        ((numberp x) (number-to-string x))
        (t (prin1-to-string x))))

(defun propagator-orbit--check-edn (x)
  (format (concat "{:sigma %s :image %s :seed %d "
                  ":gen-pathwise %s :phe-pathwise %s "
                  ":first-gen-mismatch %s :first-phe-mismatch %s}")
          (propagator-orbit--vector-edn (plist-get x :sigma))
          (propagator-orbit--vector-edn (plist-get x :image))
          (plist-get x :seed)
          (propagator-orbit--value-edn (plist-get x :gen-pathwise))
          (propagator-orbit--value-edn (plist-get x :phe-pathwise))
          (propagator-orbit--value-edn (plist-get x :first-gen-mismatch))
          (propagator-orbit--value-edn (plist-get x :first-phe-mismatch))))

(defun propagator-orbit-probe ()
  (let* ((perms (list [2 3 4 5 6 7 0 1]
                      [1 2 3 0 5 6 7 4]
                      [1 2 0 4 5 6 7 3]
                      [5 1 2 7 6 0 4 3]
                      [6 7 4 0 5 1 2 3]
                      [3 1 2 5 7 0 6 4]))
         (seeds '(0 1 2 3))
         (mirror (cl-loop for perm in perms append
                          (cl-loop for seed in seeds collect
                                   (propagator-orbit--one-check
                                    perm seed propagator-orbit--mirror :mirror))))
         (complement (cl-loop for perm in perms append
                              (cl-loop for seed in seeds collect
                                       (propagator-orbit--one-check
                                        perm seed propagator-orbit--complement
                                        :complement))))
         (mirror-pass (cl-every (lambda (x)
                                  (and (plist-get x :gen-pathwise)
                                       (plist-get x :phe-pathwise))) mirror))
         (complement-pass (cl-every (lambda (x)
                                      (and (plist-get x :gen-pathwise)
                                           (plist-get x :phe-pathwise))) complement)))
    (princ
     (format (concat "{:protocol {:width 24 :steps 24 :seeds [0 1 2 3] "
                     ":sigmas 6 :matched-initial-conditions true "
                     ":matched-transformed-mutation-choices true} "
                     ":actions {:mirror %s :complement %s} "
                     ":mirror-pathwise %s :complement-pathwise %s "
                     ":mirror-checks [%s] :complement-checks [%s]}\n")
             (propagator-orbit--vector-edn propagator-orbit--mirror)
             (propagator-orbit--vector-edn propagator-orbit--complement)
             (if mirror-pass "true" "false")
             (if complement-pass "true" "false")
             (mapconcat #'propagator-orbit--check-edn mirror " ")
             (mapconcat #'propagator-orbit--check-edn complement " ")))))

(provide 'propagator-orbit-probe)
;;; propagator_orbit_probe.el ends here
