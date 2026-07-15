;;; -*- lexical-binding: t -*-
;;; Held-out mechanism test for the original MetaCA propagator harness.

(require 'cl-lib)
(require 'run)

(defconst mechanism-neighbourhoods
  ["000" "001" "010" "100" "011" "101" "110" "111"])

(defconst mechanism-mirror [0 3 2 1 6 5 4 7])

(defconst mechanism-live-twin [2 3 4 5 6 7 0 1])
(defconst mechanism-dead-twin [1 2 3 0 5 6 7 4])

;; Maps the cycles (0 2 4 6)(1 3 5 7) to (0 1 2 3)(4 5 6 7).
(defconst mechanism-twin-conjugacy [0 4 1 5 2 6 3 7])

(defconst mechanism-cases
  ;; Predictions were fixed before any held-out run.  P predicts life iff at
  ;; most three edges preserve neighbourhood Hamming weight and 000/111 are
  ;; in different permutation orbits.
  '((:name "rotate+2-anchor" :perm [2 3 4 5 6 7 0 1]
     :held-out nil :prediction live)
    (:name "two-4-cycles-anchor" :perm [1 2 3 0 5 6 7 4]
     :held-out nil :prediction dead)
    (:name "3+5-cycles-anchor" :perm [1 2 0 4 5 6 7 3]
     :held-out nil :prediction live)
    (:name "flip-left" :perm [3 5 6 0 7 1 2 4]
     :held-out t :prediction live)
    (:name "flip-centre" :perm [2 4 0 6 1 7 3 5]
     :held-out t :prediction live)
    (:name "flip-right" :perm [1 0 4 5 2 3 7 6]
     :held-out t :prediction live)
    (:name "left-right-mirror" :perm [0 3 2 1 6 5 4 7]
     :held-out t :prediction dead)
    (:name "complement-neighbourhood" :perm [7 6 5 4 3 2 1 0]
     :held-out t :prediction dead)
    (:name "rotate-neighbourhood-left" :perm [0 2 3 1 6 4 5 7]
     :held-out t :prediction dead)
    (:name "rotate-neighbourhood-right" :perm [0 3 1 2 5 6 4 7]
     :held-out t :prediction dead)
    (:name "centre-xor-left" :perm [0 1 2 6 4 7 3 5]
     :held-out t :prediction dead)
    (:name "centre-xor-right" :perm [0 4 2 3 1 7 6 5]
     :held-out t :prediction dead)
    (:name "right-xor-left" :perm [0 1 2 5 4 3 7 6]
     :held-out t :prediction dead)))

(defun mechanism-weight (bits)
  (cl-count ?1 bits))

(defun mechanism-hamming-distance (a b)
  (cl-loop for k below 3
           count (/= (aref a k) (aref b k))))

(defun mechanism-distance-histogram (perm)
  (let ((distances
         (cl-loop for k below 8
                  collect (mechanism-hamming-distance
                           (aref mechanism-neighbourhoods k)
                           (aref mechanism-neighbourhoods (aref perm k))))))
    (cl-loop for distance from 0 to 3
             collect (cl-count distance distances))))

(defun mechanism-weight-preserving-edges (perm)
  (cl-loop for k below 8
           count (= (mechanism-weight (aref mechanism-neighbourhoods k))
                    (mechanism-weight
                     (aref mechanism-neighbourhoods (aref perm k))))))

(defun mechanism-same-orbit-p (perm a b)
  (let ((cursor a) (seen nil) (found nil))
    (while (and (not found) (not (memq cursor seen)))
      (if (= cursor b)
          (setq found t)
        (push cursor seen)
        (setq cursor (aref perm cursor))))
    found))

(defun mechanism-property-p (perm)
  (and (<= (mechanism-weight-preserving-edges perm) 3)
       (not (mechanism-same-orbit-p perm 0 7))))

(defun mechanism-commutes-with-mirror-p (perm)
  (cl-loop for k below 8
           always (= (aref mechanism-mirror (aref perm k))
                     (aref perm (aref mechanism-mirror k)))))

(defun mechanism-random-byte ()
  (apply #'string
         (cl-loop repeat 8 collect (if (= 0 (random 2)) ?0 ?1))))

(defun mechanism-integer-byte (n)
  (apply #'string
         (cl-loop for k from 7 downto 0
                  collect (if (= 0 (logand n (ash 1 k))) ?0 ?1))))

(defun mechanism-step (rule perm k)
  (bitset rule (aref perm k) (binv (bitref rule k))))

(defun mechanism-relabel-byte (rule relabeling)
  (let ((result rule))
    (dotimes (k 8)
      (setq result (bitset result (aref relabeling k) (bitref rule k))))
    result))

(defun mechanism-twin-transition-conjugacy-p ()
  "Exhaustively compare all 256 bytes and all eight possible random choices."
  (cl-loop for n below 256
           for rule = (mechanism-integer-byte n)
           always
           (cl-loop for k below 8
                    always
                    (string=
                     (mechanism-relabel-byte
                      (mechanism-step rule mechanism-live-twin k)
                      mechanism-twin-conjugacy)
                     (mechanism-step
                      (mechanism-relabel-byte rule mechanism-twin-conjugacy)
                      mechanism-dead-twin
                      (aref mechanism-twin-conjugacy k))))))

(defun mechanism-isolated-finals (name perm)
  (let (finals)
    (dotimes (seed 32)
      (random (format "mechanism-isolated-%s-%d" name seed))
      (let ((rule (mechanism-random-byte))
            (prop (make-perm-prop perm)))
        (dotimes (_ 400)
          (setq rule (funcall prop rule nil)))
        (cl-pushnew rule finals :test #'string=)))
    (sort finals #'string<)))

(defun mechanism-run-case (case)
  (let* ((name (plist-get case :name))
         (perm (plist-get case :perm))
         (runs (cl-loop for seed below 4
                        collect (run-propagator perm seed 60 120))))
    (append case
            (list :property (mechanism-property-p perm)
                  :weight-preserving
                  (mechanism-weight-preserving-edges perm)
                  :distance-histogram (mechanism-distance-histogram perm)
                  :extremes-same-orbit
                  (mechanism-same-orbit-p perm 0 7)
                  :commutes-with-mirror
                  (mechanism-commutes-with-mirror-p perm)
                  :deaths (mapcar (lambda (r) (plist-get r :death)) runs)
                  :rules (mapcar (lambda (r) (plist-get r :rules)) runs)
                  :activities (mapcar (lambda (r) (plist-get r :activity)) runs)
                  :isolated-finals (mechanism-isolated-finals name perm)))))

(defun mechanism-main ()
  (let ((output (or (getenv "MECHANISM_OUT")
                    "data/propagator-mechanism-results.el"))
        (conjugate (mechanism-twin-transition-conjugacy-p))
        (results (mapcar #'mechanism-run-case mechanism-cases)))
    (unless conjugate
      (error "Opposite-outcome twin transition graphs are not conjugate"))
    (with-temp-file output
      (insert ";; Generated by scripts/propagator_mechanism.el\n")
      (insert (format ";; twin-transition-conjugacy-check: %S\n" conjugate))
      (pp results (current-buffer)))
    (princ (format "wrote %s (%d cases)\n" output (length results)))))

(mechanism-main)
