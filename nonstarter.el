;;; nonstarter.el --- Nonstarter Emacs client -*- lexical-binding: t; -*-

(defgroup nonstarter nil
  "Emacs client for the Nonstarter HTTP API and local DB prototype."
  :group 'applications)

(require 'json)
(require 'pp)
(require 'url)
(require 'subr-x)
(require 'seq)

(defcustom nonstarter-base-url "http://127.0.0.1:7778"
  "Base URL for the Nonstarter server."
  :type 'string
  :group 'nonstarter)

(defcustom nonstarter-timeout 10
  "Timeout in seconds for HTTP requests."
  :type 'integer
  :group 'nonstarter)

(defcustom nonstarter-personal-root nil
  "Path to futon5a repo. If nil, derived from FUTON5A_ROOT or sibling of futon5."
  :type '(choice (const :tag "Auto" nil) directory)
  :group 'nonstarter)

(defcustom nonstarter-personal-config nil
  "Optional path to a personal config file with category settings."
  :type '(choice (const :tag "Auto" nil) file)
  :group 'nonstarter)

(defcustom nonstarter-personal-db-path nil
  "Path to personal Nonstarter SQLite DB.
If nil, uses NONSTARTER_PERSONAL_DB or ~/code/storage/futon5a/nonstarter.db."
  :type '(choice (const :tag "Auto" nil) file)
  :group 'nonstarter)

(defcustom nonstarter-personal-autostart t
  "When non-nil, auto-start the personal API server if needed."
  :type 'boolean
  :group 'nonstarter)

(defvar nonstarter-personal-server-process nil
  "Process handle for the personal API server.")

(defvar nonstarter-personal-server-port 7778
  "Port for the personal API server.")

(defcustom nonstarter-category-descriptions
  '(("q1" . "Quadrant 1")
    ("q2" . "Quadrant 2")
    ("q3" . "Quadrant 3")
    ("q4" . "Quadrant 4"))
  "Descriptions for dashboard categories."
  :type '(alist :key-type string :value-type string)
  :group 'nonstarter)

(defcustom nonstarter-category-groups
  '(("q1" . ("q1"))
    ("q2" . ("q2"))
    ("q3" . ("q3"))
    ("q4" . ("q4")))
  "Nested category groupings shown in the dashboard."
  :type '(alist :key-type string :value-type (repeat string))
  :group 'nonstarter)

(defcustom nonstarter-category-subitems
  nil
  "Extra nested items shown under specific categories."
  :type '(alist :key-type string :value-type (repeat string))
  :group 'nonstarter)

(defcustom nonstarter-show-deliverable-meta t
  "When non-nil, show deliverable due dates and status in the dashboard."
  :type 'boolean
  :group 'nonstarter)

(defcustom nonstarter-show-deliverable-mana t
  "When non-nil, show deliverable mana estimates and votes in the dashboard."
  :type 'boolean
  :group 'nonstarter)

(defface nonstarter-mana-face
  '((t :foreground "#7B4DFF"))
  "Face for mana annotations."
  :group 'nonstarter)

(defface nonstarter-mana-estimate-face
  '((t :foreground "#2E8B57"))
  "Face for mana estimate annotations."
  :group 'nonstarter)

(defface nonstarter-mana-flow-face
  '((t :foreground "#3FAF70"))
  "Face for flowed mana estimate annotations."
  :group 'nonstarter)

(defcustom nonstarter-quadrant-mana-estimates
  nil
  "Estimated mana allocation for quadrant group headings."
  :type '(alist :key-type string :value-type number)
  :group 'nonstarter)

(defcustom nonstarter-biocompatibility-hold
  nil
  "Category weights used to compute biocompatibility burn/hold from bids."
  :type '(alist :key-type string :value-type number)
  :group 'nonstarter)

(defcustom nonstarter-show-average-day t
  "When non-nil, show the average-day SVG in the dashboard."
  :type 'boolean
  :group 'nonstarter)

(defcustom nonstarter-deliverable-category-map
  nil
  "Map deliverable areas to dashboard categories."
  :type '(alist :key-type string :value-type string)
  :group 'nonstarter)

(defcustom nonstarter-default-weekly-bids
  nil
  "Default weekly bids to apply."
  :type '(alist :key-type string :value-type number)
  :group 'nonstarter)

;;; ---------------------------------------------------------------------------
;;; Portfolio configuration
;;; ---------------------------------------------------------------------------

(defcustom nonstarter-futon5-api-url "http://127.0.0.1:7072"
  "Base URL for the futon5 heartbeat API server."
  :type 'string
  :group 'nonstarter)

(defcustom nonstarter-futon3c-api-url "http://127.0.0.1:7070"
  "Base URL for the futon3c portfolio AIF server."
  :type 'string
  :group 'nonstarter)

(defcustom nonstarter-effort-band-thresholds
  '((1.0 . "trivial")
    (3.0 . "easy")
    (8.0 . "medium")
    (20.0 . "hard")
    (1000.0 . "epic"))
  "Hours-to-effort-band mapping for T-8 compression.
Each entry is (THRESHOLD . BAND); first entry whose threshold >= hours wins."
  :type '(alist :key-type number :value-type string)
  :group 'nonstarter)

(defcustom nonstarter-portfolio-missions nil
  "Mission identifiers for portfolio bid/clear prompting.
Each entry is (MISSION-ID . DESCRIPTION).
If nil, portfolio bid form fetches missions from futon3c."
  :type '(alist :key-type string :value-type string)
  :group 'nonstarter)

(defcustom nonstarter-portfolio-category-mission-map nil
  "Map personal categories to portfolio mission IDs for T-8 compression.
Each entry is (CATEGORY . MISSION-ID)."
  :type '(alist :key-type string :value-type string)
  :group 'nonstarter)

(defvar nonstarter-portfolio-actions
  '("work-on" "review" "consolidate" "upvote" "wait")
  "Portfolio action types, aligned with futon3c policy arena.")

(defvar nonstarter-portfolio-outcomes
  '("complete" "partial" "abandoned")
  "Possible outcomes for portfolio clear entries.")

(defvar nonstarter-portfolio-modes
  '("BUILD" "MAINTAIN" "CONSOLIDATE")
  "Portfolio operating modes.")

;;; ---------------------------------------------------------------------------
;;; Local prototype (futon5) helpers
;;; ---------------------------------------------------------------------------

(defcustom nonstarter-root nil
  "Path to futon5 repo. If nil, derived from nonstarter.el location or FUTON5_ROOT."
  :type '(choice (const :tag "Auto" nil) directory)
  :group 'nonstarter)

(defcustom nonstarter-storage-root nil
  "Path to shared storage root.
If nil, uses FUTON_STORAGE_ROOT or ~/code/storage."
  :type '(choice (const :tag "Auto" nil) directory)
  :group 'nonstarter)

(defcustom nonstarter-db-path nil
  "Path to Nonstarter SQLite DB.
If nil, uses FUTON5_NONSTARTER_DB or ~/code/storage/futon5/nonstarter.db."
  :type '(choice (const :tag "Auto" nil) file)
  :group 'nonstarter)

(defcustom nonstarter-cli-command "bb"
  "Command used to run local Clojure scripts."
  :type 'string
  :group 'nonstarter)

(defun nonstarter--root ()
  (expand-file-name
   (or (and (stringp nonstarter-root) (not (nonstarter--blank-p nonstarter-root)) nonstarter-root)
       (getenv "FUTON5_ROOT")
       (file-name-directory (or load-file-name buffer-file-name default-directory)))))

(defun nonstarter--storage-root ()
  (expand-file-name
   (or (and (stringp nonstarter-storage-root)
            (not (nonstarter--blank-p nonstarter-storage-root))
            nonstarter-storage-root)
       (getenv "FUTON_STORAGE_ROOT")
       "~/code/storage")))

(defun nonstarter--storage-db-path (repo-key)
  (expand-file-name (concat repo-key "/nonstarter.db")
                    (nonstarter--storage-root)))

(defun nonstarter--db ()
  (expand-file-name
   (or (and (stringp nonstarter-db-path) (not (nonstarter--blank-p nonstarter-db-path)) nonstarter-db-path)
       (getenv "FUTON5_NONSTARTER_DB")
       (nonstarter--storage-db-path "futon5"))))

(defun nonstarter--personal-root ()
  (expand-file-name
   (or (and (stringp nonstarter-personal-root)
            (not (nonstarter--blank-p nonstarter-personal-root))
            nonstarter-personal-root)
       (getenv "FUTON5A_ROOT")
       (let ((base (file-name-directory (or load-file-name buffer-file-name default-directory))))
         (expand-file-name "../futon5a" base)))))

(defun nonstarter--personal-config-path ()
  (or nonstarter-personal-config
      (getenv "NONSTARTER_PERSONAL_CONFIG")
      (let ((root (nonstarter--personal-root)))
        (when root
          (expand-file-name "config/nonstarter-personal.el" root)))))

(defun nonstarter--load-personal-config ()
  (let ((path (nonstarter--personal-config-path)))
    (when (and path (file-exists-p path))
      (load-file path))))

(nonstarter--load-personal-config)

(defun nonstarter--personal-db ()
  (expand-file-name
   (or (and (stringp nonstarter-personal-db-path)
            (not (nonstarter--blank-p nonstarter-personal-db-path))
            nonstarter-personal-db-path)
       (getenv "NONSTARTER_PERSONAL_DB")
       (nonstarter--storage-db-path "futon5a"))))

(defun nonstarter-personal-server-running-p ()
  (and nonstarter-personal-server-process
       (process-live-p nonstarter-personal-server-process)))

(defun nonstarter-personal-server-start (&optional port db)
  "Start the personal API server in Emacs."
  (interactive)
  (let* ((port (or port nonstarter-personal-server-port))
         (db (or db (nonstarter--personal-db)))
         (default-directory (nonstarter--personal-root)))
    (setq nonstarter-personal-server-port port)
    (unless (nonstarter-personal-server-running-p)
      (setq nonstarter-personal-server-process
            (make-process :name "nonstarter-personal-api"
                          :buffer "*nonstarter-personal-api*"
                          :command (list "bb" "-m" "personal.api"
                                         "--port" (number-to-string port)
                                         "--db" db)
                          :noquery t))
      (setq nonstarter-base-url (format "http://127.0.0.1:%s" port))
      (message "Nonstarter personal API started on %s (db %s)" port db))
    nonstarter-personal-server-process))

(defun nonstarter-personal-server-stop ()
  "Stop the personal API server."
  (interactive)
  (when (nonstarter-personal-server-running-p)
    (kill-process nonstarter-personal-server-process)
    (setq nonstarter-personal-server-process nil)
    (message "Nonstarter personal API stopped.")))

(defun nonstarter-personal-chart-refresh ()
  "Regenerate the average-day SVG."
  (interactive)
  (let ((default-directory (nonstarter--personal-root)))
    (make-process :name "nonstarter-day-chart"
                  :buffer "*nonstarter-day-chart*"
                  :command (list "python3" "scripts/nonstarter_day_chart.py")
                  :noquery t)
    (message "Regenerating average-day SVG...")))

(defun nonstarter-personal-deliverables-ingest (&optional prune)
  "Re-ingest personal deliverables from EDN and refresh the dashboard.
With prefix arg PRUNE, remove DB entries not present in the file."
  (interactive "P")
  (let ((default-directory (nonstarter--personal-root)))
    (make-process
     :name "nonstarter-deliverables-ingest"
     :buffer "*nonstarter-deliverables*"
     :command (append (list "bb" "scripts/personal_ingest_deliverables.clj")
                      (when prune (list "--prune")))
     :noquery t
     :sentinel (lambda (proc _event)
                 (when (and (eq (process-status proc) 'exit)
                            (= (process-exit-status proc) 0))
                   (message "Deliverables ingested.")
                   (nonstarter-dashboard)))))
  (message "Ingesting deliverables..."))

(defun nonstarter--maybe-start-personal-server ()
  (when (and nonstarter-personal-autostart
             (string-match-p "127\\.0\\.0\\.1" nonstarter-base-url))
    (nonstarter-personal-server-start)))

(defun nonstarter--blank-p (s)
  (or (null s)
      (not (stringp s))
      (string-match-p "\\`[ \t\n\r]*\\'" s)))

(defun nonstarter--show-text (title text)
  (let ((buf (get-buffer-create "*Nonstarter*")))
    (with-current-buffer buf
      (read-only-mode -1)
      (erase-buffer)
      (insert title "\n\n")
      (insert (or text ""))
      (goto-char (point-min))
      (read-only-mode 1))
    (display-buffer buf)))

(defun nonstarter--parse-edn-lines (text)
  (let ((forms '()))
    (dolist (line (split-string (or text "") "\n" t))
      (when (string-prefix-p "{" line)
        (condition-case nil
            (let ((form (car (read-from-string line))))
              (when (listp form)
                (push form forms)))
          (error nil))))
    (nreverse forms)))

(defun nonstarter--alist-val (key alist)
  (or (alist-get key alist)
      (alist-get (intern (format "%s" key)) alist)
      (alist-get (format "%s" key) alist)))

(defun nonstarter--format-hypotheses (text)
  (let ((items (nonstarter--parse-edn-lines text)))
    (if (null items)
        (or text "")
      (mapconcat
       (lambda (h)
         (let ((title (or (nonstarter--alist-val 'title h) ""))
               (status (or (nonstarter--alist-val 'status h) ""))
               (hid (or (nonstarter--alist-val 'id h) ""))
               (statement (or (nonstarter--alist-val 'statement h) ""))
               (context (or (nonstarter--alist-val 'context h) "")))
           (concat
            (format "- %s [%s]\n" title status)
            (format "  id: %s\n" hid)
            (format "  statement: %s\n" statement)
            (when (not (nonstarter--blank-p context))
              (format "  context: %s\n" context)))))
       items
       "\n"))))

(defun nonstarter--format-studies (text)
  (let ((items (nonstarter--parse-edn-lines text)))
    (if (null items)
        (or text "")
      (mapconcat
       (lambda (s)
         (let ((sid (or (nonstarter--alist-val 'id s) ""))
               (hyp (or (nonstarter--alist-val 'hypothesis_id s) ""))
               (name (or (nonstarter--alist-val 'study_name s) ""))
               (status (or (nonstarter--alist-val 'status s) "")))
           (concat
            (format "- %s [%s]\n" name status)
            (format "  id: %s\n" sid)
            (format "  hypothesis: %s\n" hyp))))
       items
       "\n"))))

(defun nonstarter--run-script (script-path ns &rest args)
  (let* ((default-directory (nonstarter--root))
         (expr (concat "(load-file \"" script-path "\") "
                       "(" ns "/-main "
                       (mapconcat (lambda (s) (format "%S" s)) args " ")
                       ")"))
         (cli (file-name-nondirectory nonstarter-cli-command))
         (cmd (mapconcat #'shell-quote-argument
                         (if (string= cli "bb")
                             (list nonstarter-cli-command "-e" expr)
                           (list nonstarter-cli-command "-M" "-e" expr))
                         " "))
         (raw (shell-command-to-string cmd)))
    (string-trim-left
     (replace-regexp-in-string "^SLF4J.*\n?" "" raw))))

(defun nonstarter-hypothesis-register (title statement &optional context status mana)
  "Register a hypothesis in the local Nonstarter DB."
  (interactive
   (list (read-string "Title: ")
         (read-string "Statement: ")
         (read-string "Context (required): ")
         (read-string "Status (default active): ")
         (read-string "Mana estimate (required): ")))
  (let* ((args (list "--db" (nonstarter--db)
                     "register"
                     "--title" title
                     "--statement" statement))
         (args (if (nonstarter--blank-p context) args (append args (list "--context" context))))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (args (if (nonstarter--blank-p mana) args (append args (list "--mana" mana))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_hypothesis.clj"
                        "scripts.nonstarter-hypothesis"
                        args)))
    (nonstarter--show-text "Nonstarter Hypothesis Register" output)))

(defun nonstarter-hypothesis-update (hypothesis-id status &optional priority mana)
  "Update hypothesis status in the local Nonstarter DB."
  (interactive
   (list (read-string "Hypothesis ID: ")
         (read-string "Status (optional): ")
         (read-string "Priority (optional): ")
         (read-string "Mana estimate (optional): ")))
  (let* ((args (list "--db" (nonstarter--db)
                     "update"
                     "--id" hypothesis-id))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (args (if (nonstarter--blank-p priority) args (append args (list "--priority" priority))))
         (args (if (nonstarter--blank-p mana) args (append args (list "--mana" mana))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_hypothesis.clj"
                        "scripts.nonstarter-hypothesis"
                        args)))
    (nonstarter--show-text "Nonstarter Hypothesis Update" output)))

(defun nonstarter-hypothesis-vote (hypothesis-id &optional voter weight note)
  "Vote on a hypothesis (precision signal)."
  (interactive
   (list (read-string "Hypothesis ID: ")
         (read-string "Voter (optional): ")
         (read-string "Weight (default 1): ")
         (read-string "Note (optional): ")))
  (let* ((args (list "--db" (nonstarter--db)
                     "vote"
                     "--id" hypothesis-id))
         (args (if (nonstarter--blank-p voter) args (append args (list "--voter" voter))))
         (args (if (nonstarter--blank-p weight) args (append args (list "--weight" weight))))
         (args (if (nonstarter--blank-p note) args (append args (list "--note" note))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_hypothesis.clj"
                        "scripts.nonstarter-hypothesis"
                        args)))
    (nonstarter--show-text "Nonstarter Hypothesis Vote" output)))

(defun nonstarter-hypothesis-list (&optional status)
  "List hypotheses from the local Nonstarter DB."
  (interactive (list (read-string "Status (optional): ")))
  (let* ((args (list "--db" (nonstarter--db) "list"))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_hypothesis.clj"
                        "scripts.nonstarter-hypothesis"
                        args)))
    (nonstarter--show-text "Nonstarter Hypotheses" output)))

(defun nonstarter-study-register (hypothesis-id study-name &optional design metrics seeds status results notes priority mana)
  "Register a study preregistration in the local Nonstarter DB."
  (interactive
   (list (read-string "Hypothesis ID: ")
         (read-string "Study name: ")
         (read-string "Design EDN (optional): ")
         (read-string "Metrics EDN (optional): ")
         (read-string "Seeds EDN (optional): ")
         (read-string "Status (default preregistered): ")
         (read-string "Results EDN (optional): ")
         (read-string "Notes (optional): ")
         (read-string "Priority (optional): ")
         (read-string "Mana estimate (optional): ")))
  (let* ((args (list "--db" (nonstarter--db)
                     "register"
                     "--hypothesis-id" hypothesis-id
                     "--study-name" study-name))
         (args (if (nonstarter--blank-p design) args (append args (list "--design" design))))
         (args (if (nonstarter--blank-p metrics) args (append args (list "--metrics" metrics))))
         (args (if (nonstarter--blank-p seeds) args (append args (list "--seeds" seeds))))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (args (if (nonstarter--blank-p results) args (append args (list "--results" results))))
         (args (if (nonstarter--blank-p notes) args (append args (list "--notes" notes))))
         (args (if (nonstarter--blank-p priority) args (append args (list "--priority" priority))))
         (args (if (nonstarter--blank-p mana) args (append args (list "--mana" mana))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_study.clj"
                        "scripts.nonstarter-study"
                        args)))
    (nonstarter--show-text "Nonstarter Study Register" output)))

(defun nonstarter-study-update (study-id &optional status results notes priority mana)
  "Update a study preregistration in the local Nonstarter DB."
  (interactive
   (list (read-string "Study ID: ")
         (read-string "Status (optional): ")
         (read-string "Results EDN (optional): ")
         (read-string "Notes (optional): ")
         (read-string "Priority (optional): ")
         (read-string "Mana estimate (optional): ")))
  (let* ((args (list "--db" (nonstarter--db) "update" "--id" study-id))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (args (if (nonstarter--blank-p results) args (append args (list "--results" results))))
         (args (if (nonstarter--blank-p notes) args (append args (list "--notes" notes))))
         (args (if (nonstarter--blank-p priority) args (append args (list "--priority" priority))))
         (args (if (nonstarter--blank-p mana) args (append args (list "--mana" mana))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_study.clj"
                        "scripts.nonstarter-study"
                        args)))
    (nonstarter--show-text "Nonstarter Study Update" output)))

(defun nonstarter-study-list (&optional hypothesis-id)
  "List study preregistrations from the local Nonstarter DB."
  (interactive (list (read-string "Hypothesis ID (optional): ")))
  (let* ((args (list "--db" (nonstarter--db) "list"))
         (args (if (nonstarter--blank-p hypothesis-id)
                   args
                 (append args (list "--hypothesis-id" hypothesis-id))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_study.clj"
                        "scripts.nonstarter-study"
                        args)))
    (nonstarter--show-text "Nonstarter Studies" output)))

(defun nonstarter-mana-donate (amount &optional donor note)
  "Donate mana to the local Nonstarter pool."
  (interactive
   (list (read-string "Amount: ")
         (read-string "Donor (optional): ")
         (read-string "Note (optional): ")))
  (let* ((args (list "--db" (nonstarter--db)
                     "donate"
                     "--amount" amount))
         (args (if (nonstarter--blank-p donor) args (append args (list "--donor" donor))))
         (args (if (nonstarter--blank-p note) args (append args (list "--note" note))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_mana.clj"
                        "scripts.nonstarter-mana"
                        args)))
    (nonstarter--show-text "Nonstarter Mana Donate" output)))

(defun nonstarter-mana-pool ()
  "Show mana pool stats."
  (interactive)
  (let* ((output (nonstarter--run-script
                  "scripts/nonstarter_mana.clj"
                  "scripts.nonstarter-mana"
                  "--db" (nonstarter--db)
                  "pool"
                  "--format" "text")))
    (nonstarter--show-text "Nonstarter Mana Pool" output)))

(defun nonstarter-mana-sospeso (action confidence cost &optional session)
  "Give sospeso for a lower-confidence action.

Donates (1-CONFIDENCE)*COST to the pool (pure dana).
CONFIDENCE must be one of: 0.3, 0.6, 0.8, 0.95
COST is the estimated mana cost of the action."
  (interactive
   (list (read-string "Action (1 sentence): ")
         (completing-read "Confidence: " '("0.3" "0.6" "0.8" "0.95") nil t)
         (read-string "Cost (mana): ")
         (read-string "Session ID (optional): ")))
  (let* ((args (list "--db" (nonstarter--db)
                     "sospeso"
                     "--action" action
                     "--confidence" confidence
                     "--cost" cost))
         (args (if (nonstarter--blank-p session) args (append args (list "--session" session))))
         (args (append args (list "--format" "text")))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_mana.clj"
                        "scripts.nonstarter-mana"
                        args)))
    (nonstarter--show-text "Nonstarter Sospeso" output)))

(defun nonstarter-ingest-linearized (&optional update)
  "Ingest linearized missions/excursions into the local Nonstarter DB.

With prefix arg UPDATE, refresh statuses if entries already exist."
  (interactive "P")
  (let* ((args (list "--db" (nonstarter--db)))
         (args (if update (append args (list "--update")) args))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_ingest_linearized.clj"
                        "scripts.nonstarter-ingest-linearized"
                        args)))
    (nonstarter--show-text "Nonstarter Ingest Linearized" output)))
(defun nonstarter--trim-slashes (s)
  (replace-regexp-in-string "/+$" "" s))

(defun nonstarter--url (path)
  (concat (nonstarter--trim-slashes nonstarter-base-url) path))

(defun nonstarter--json-encode (data)
  (when data
    (let ((json-encoding-pretty-print nil))
      (json-encode data))))

(defun nonstarter--stringify-keys (alist)
  (when (listp alist)
    (mapcar (lambda (pair)
              (cons (cond
                     ((symbolp (car pair)) (symbol-name (car pair)))
                     (t (car pair)))
                    (cdr pair)))
            alist)))

(defun nonstarter--json-parse-response (buffer)
  (with-current-buffer buffer
    (goto-char (point-min))
    (re-search-forward "^$" nil 'move)
    (if (>= (point) (point-max))
        nil
      (let ((json-object-type 'alist)
            (json-array-type 'list)
            (json-key-type 'symbol)
            (json-false nil))
        (condition-case nil
            (json-parse-buffer :object-type 'alist
                               :array-type 'list
                               :null-object nil
                               :false-object nil)
          (error
           (json-read)))))))

(defun nonstarter--request-body (method path &optional data headers)
  (let* ((raw-data (nonstarter--json-encode data))
         (payload (when raw-data (encode-coding-string raw-data 'utf-8)))
         (url-request-method method)
         (url-request-extra-headers (append headers
                                            '(("Content-Type" . "application/json")
                                              ("Accept" . "*/*"))))
         (url-request-data payload)
         (url (nonstarter--url path)))
    (let ((buffer (url-retrieve-synchronously url t t nonstarter-timeout)))
      (unless buffer
        (error "Nonstarter request failed: no response from %s" url))
      (with-current-buffer buffer
        (goto-char (point-min))
        (re-search-forward "^$" nil 'move)
        (let ((body (if (>= (point) (point-max))
                        nil
                      (buffer-substring-no-properties (point) (point-max)))))
          (kill-buffer (current-buffer))
          body)))))

(defun nonstarter--request (method path &optional data)
  (let* ((raw-data (nonstarter--json-encode data))
         (payload (when raw-data (encode-coding-string raw-data 'utf-8)))
         (url-request-method method)
         (url-request-extra-headers '(("Content-Type" . "application/json")
                                      ("Accept" . "application/json")))
         (url-request-data payload)
         (url (nonstarter--url path)))
    (let ((buffer (url-retrieve-synchronously url t t nonstarter-timeout)))
      (unless buffer
        (error "Nonstarter request failed: no response from %s" url))
      (with-current-buffer buffer
        (let ((parsed (nonstarter--json-parse-response (current-buffer))))
          (kill-buffer (current-buffer))
          parsed)))))

(defun nonstarter--external-request (base-url method path &optional data)
  "Make an HTTP request to an external API at BASE-URL.
Returns parsed JSON on success, nil on connection failure (graceful degradation)."
  (condition-case err
      (let* ((raw-data (nonstarter--json-encode data))
             (payload (when raw-data (encode-coding-string raw-data 'utf-8)))
             (url-request-method method)
             (url-request-extra-headers '(("Content-Type" . "application/json")
                                           ("Accept" . "application/json")))
             (url-request-data payload)
             (url (concat (nonstarter--trim-slashes base-url) path)))
        (let ((buffer (url-retrieve-synchronously url t t nonstarter-timeout)))
          (when buffer
            (with-current-buffer buffer
              (let ((parsed (nonstarter--json-parse-response (current-buffer))))
                (kill-buffer (current-buffer))
                parsed)))))
    (error
     (message "External request failed (%s%s): %s" base-url path (error-message-string err))
     nil)))

(defun nonstarter--futon5-request (method path &optional data)
  "Make an HTTP request to the futon5 heartbeat API. Returns nil on failure."
  (nonstarter--external-request nonstarter-futon5-api-url method path data))

(defun nonstarter--futon3c-request (method path &optional data)
  "Make an HTTP request to the futon3c portfolio AIF server. Returns nil on failure."
  (nonstarter--external-request nonstarter-futon3c-api-url method path data))

;;; ---------------------------------------------------------------------------
;;; T-8 compression: hours -> effort bands
;;; ---------------------------------------------------------------------------

(defun nonstarter--hours-to-effort-band (hours)
  "Compress HOURS into an effort band string using `nonstarter-effort-band-thresholds'."
  (catch 'found
    (dolist (entry nonstarter-effort-band-thresholds)
      (when (<= hours (car entry))
        (throw 'found (cdr entry))))
    "epic"))

(defun nonstarter--compress-bids-to-portfolio (category-bids)
  "Convert personal CATEGORY-BIDS (hash-table of category->hours) into portfolio bids.
Uses `nonstarter-portfolio-category-mission-map' to map categories to missions.
Returns list of alists: ((action . \"work-on\") (mission . \"M-foo\") (effort . \"hard\"))."
  (let ((portfolio-bids nil))
    (maphash
     (lambda (cat hours)
       (when (and (numberp hours) (> hours 0))
         (let ((mission (cdr (assoc cat nonstarter-portfolio-category-mission-map))))
           (when mission
             (push `((action . "work-on")
                     (mission . ,mission)
                     (effort . ,(nonstarter--hours-to-effort-band hours)))
                   portfolio-bids)))))
     category-bids)
    (nreverse portfolio-bids)))

(defun nonstarter--show (title payload)
  (let ((buf (get-buffer-create "*Nonstarter*")))
    (with-current-buffer buf
      (read-only-mode -1)
      (erase-buffer)
      (insert title "\n\n")
      (pp payload (current-buffer))
      (goto-char (point-min))
      (read-only-mode 1))
    (display-buffer (get-buffer "*Nonstarter Dashboard*"))))

(defun nonstarter--format-kv (label value)
  (format "%s: %s" label value))

(defvar nonstarter--category-face-cache (make-hash-table :test 'equal)
  "Cache of faces keyed by hex color for category display.")

(defun nonstarter--category-face (color)
  (when (and (stringp color) (not (string= color "")))
    (or (gethash color nonstarter--category-face-cache)
        (let* ((name (format "nonstarter-category-%s" (substring (md5 color) 0 8)))
               (face (make-face (intern name))))
          (set-face-attribute face nil :foreground color)
          (puthash color face nonstarter--category-face-cache)
          face))))

(defun nonstarter--svg-legend-colors (svg-body)
  "Extract (label . color) pairs from the SVG legend."
  (when (and (stringp svg-body) (not (string= svg-body "")))
    (let ((pos 0)
          (pairs '()))
      (while (string-match "<rect[^>]*width='14'[^>]*height='14'[^>]*fill='\\([^']+\\)'" svg-body pos)
        (let ((color (match-string 1 svg-body)))
          (setq pos (match-end 0))
          (when (string-match "<text[^>]*>\\([^:<]+\\):" svg-body pos)
            (let ((label (string-trim (match-string 1 svg-body))))
              (push (cons label color) pairs)
              (setq pos (match-end 0))))))
      (nreverse pairs))))

(defun nonstarter--propertize-category (label color-map &optional display)
  (let* ((color (cdr (assoc label color-map)))
         (face (nonstarter--category-face color))
         (text (or display label)))
    (if face
        (propertize text 'face face)
      text)))

(defun nonstarter--parse-number (value)
  (cond
   ((numberp value) (float value))
   ((stringp value)
    (condition-case nil
        (string-to-number value)
      (error nil)))
   (t nil)))

(defun nonstarter--hold-from-bids (bids)
  (let ((out nil))
    (dolist (entry nonstarter-biocompatibility-hold)
      (let* ((cat (car entry))
             (weight (cdr entry))
             (raw (alist-get cat bids nil nil #'string=))
             (hours (nonstarter--parse-number raw)))
        (when (and (numberp hours) (numberp weight))
          (push (cons cat (* hours weight)) out))))
    (nreverse out)))

(defun nonstarter--hold-total (hold)
  (apply #'+ 0.0 (mapcar (lambda (entry) (float (cdr entry))) hold)))

(defun nonstarter--hold-amount (hold key)
  (cdr (assoc key hold)))

(defun nonstarter--hold-weight (category)
  (let ((value (alist-get category nonstarter-biocompatibility-hold nil nil #'string=)))
    (when (numberp value) value)))

(defun nonstarter--category-funding-note (category hold epoch-weeks)
  (let* ((weight (nonstarter--hold-weight category))
         (per-week (cdr (assoc category hold))))
    (when (and (numberp weight) (numberp per-week) (numberp epoch-weeks))
      (let* ((total (* per-week epoch-weeks))
             (label (if (= weight 1.0)
                        "fully funded"
                      (format "%.0f%% funded" (* 100.0 weight))))
             (text (format " (%s %s)" label (nonstarter--format-amount total))))
        (propertize text 'face 'nonstarter-mana-face)))))

(defun nonstarter--category-key (category)
  (format "%s" (or category "")))

(defun nonstarter--normalize-budget-map (raw)
  (when (and raw (listp raw))
    (let ((out nil))
      (dolist (pair raw)
        (when (consp pair)
          (let* ((key (nonstarter--category-key (car pair)))
                 (val (nonstarter--parse-number (cdr pair))))
            (when (and (not (string= key "")) (numberp val))
              (push (cons key val) out)))))
      (nreverse out))))

(defun nonstarter--budget-lookup (budgets category)
  (let ((key (nonstarter--category-key category)))
    (cdr (assoc key budgets))))

(defun nonstarter--remaining-total (remaining)
  (cond
   ((numberp remaining) remaining)
   ((listp remaining)
    (apply #'+ 0.0 (mapcar (lambda (pair)
                             (let ((v (cdr pair)))
                               (if (numberp v) v 0.0)))
                           remaining)))
   (t 0.0)))

(defun nonstarter--category-budget-note (category budgets remaining)
  (let ((budget (nonstarter--budget-lookup budgets category)))
    (when (numberp budget)
      (let* ((rem (or (nonstarter--budget-lookup remaining category) budget))
             (text (format " (budget %s / remaining %s)"
                           (nonstarter--format-amount budget)
                           (nonstarter--format-amount rem))))
        (propertize text 'face 'nonstarter-mana-face)))))

(defun nonstarter--titleize (s)
  (let ((parts (split-string (format "%s" s) "-" t)))
    (mapconcat #'capitalize parts " ")))

(defun nonstarter--hold-items (hold week-id)
  (let ((items nil)
        (week (or week-id "week")))
    (dolist (entry hold)
      (let* ((cat (car entry))
             (hours (cdr entry)))
        (push `((id . ,(format "hold:%s:%s" cat week))
                (category . ,cat)
                (title . ,(format "%s hold" (nonstarter--titleize cat)))
                (status . "hold")
                (hold . t)
                (mana . ,hours)
                (votes . 0))
              items)))
    (nreverse items)))

(defun nonstarter--deliverable-mana (item)
  (nonstarter--parse-number (alist-get 'mana item)))

(defun nonstarter--deliverable-votes (item)
  (or (nonstarter--parse-number (alist-get 'votes item))
      0.0))

(defun nonstarter--deliverable-due-key (item)
  (or (alist-get 'due item) "9999-12-31"))

(defun nonstarter--order-deliverables (items)
  (sort (copy-sequence items)
        (lambda (a b)
          (let ((va (nonstarter--deliverable-votes a))
                (vb (nonstarter--deliverable-votes b)))
            (if (/= va vb)
                (> va vb)
              (string< (nonstarter--deliverable-due-key a)
                       (nonstarter--deliverable-due-key b)))))))

(defun nonstarter--award-deliverables (items budget)
  (let ((awarded (make-hash-table :test 'equal))
        (remaining (if (numberp budget) budget 0.0)))
    (dolist (item (nonstarter--order-deliverables
                   (seq-remove (lambda (it) (alist-get 'hold it)) items)))
      (let ((mana (nonstarter--deliverable-mana item)))
        (when (and (numberp mana) (<= mana remaining))
          (puthash (alist-get 'id item) t awarded)
          (setq remaining (- remaining mana)))))
    (list awarded remaining)))

(defun nonstarter--award-deliverables-by-category (items budgets)
  (let ((awarded (make-hash-table :test 'equal))
        (remaining budgets)
        (groups (make-hash-table :test 'equal)))
    (dolist (item items)
      (unless (alist-get 'hold item)
        (let ((cat (nonstarter--category-key (alist-get 'category item))))
          (puthash cat (cons item (gethash cat groups)) groups))))
    (dolist (pair budgets)
      (let* ((cat (car pair))
             (budget (cdr pair))
             (items* (gethash cat groups)))
        (when (and (numberp budget) items*)
          (dolist (item (nonstarter--order-deliverables items*))
            (let ((mana (nonstarter--deliverable-mana item)))
              (when (and (numberp mana) (<= mana budget))
                (puthash (alist-get 'id item) t awarded)
                (setq budget (- budget mana))))))
        (setq remaining (assq-delete-all cat remaining))
        (when (numberp budget)
          (push (cons cat budget) remaining))))
    (list awarded remaining)))

(defun nonstarter--deliverable-tag (item awarded)
  (let ((mana (nonstarter--deliverable-mana item)))
    (cond
     ((alist-get 'hold item) "burned")
     ((numberp mana) (if (and awarded (gethash (alist-get 'id item) awarded)) "awarded" "gated"))
     (t "unestimated"))))

(defun nonstarter--deliverable-tag-with-estimate (item awarded estimate)
  (let ((mana (nonstarter--deliverable-mana item)))
    (cond
     ((alist-get 'hold item) "burned")
     ((numberp mana) (if (and awarded (gethash (alist-get 'id item) awarded)) "awarded" "gated"))
     ((numberp estimate) "estimated")
     (t "unestimated"))))

(defun nonstarter--format-estimate (value)
  (if (and (numberp value) (= (floor value) value))
      (format "%.0f" value)
    (format "%.1f" value)))

(defun nonstarter--format-estimate-note (value &optional face)
  (when (numberp value)
    (concat " (estimate "
            (propertize (nonstarter--format-estimate value)
                        'face (or face 'nonstarter-mana-estimate-face))
            ")")))

(defun nonstarter--read-hours (prompt &optional default allow-blank)
  (let* ((prompt (if (numberp default)
                     (format "%s[%s] " prompt (nonstarter--format-hours default))
                   prompt))
         (value nil))
    (while (eq value :retry)
      (setq value nil))
    (while (null value)
      (let ((input (read-string prompt)))
        (cond
         ((string= input "")
          (setq value (if allow-blank
                          nil
                        (or default 0.0))))
         (t
          (let ((parsed (nonstarter--parse-number input)))
            (if (numberp parsed)
                (setq value parsed)
              (message "Please enter a number.")
              (sit-for 0.2)))))))
    value))

(defun nonstarter--add-total (totals category hours)
  (let ((current (gethash category totals 0.0)))
    (puthash category (+ current hours) totals)))

(defun nonstarter--deliverable-key (item)
  (list (alist-get 'area item) (alist-get 'title item) (alist-get 'due item)))

(defun nonstarter--compute-leaf-estimates (deliverables-by-category)
  (let ((category-estimates (make-hash-table :test 'equal))
        (subitem-estimates (make-hash-table :test 'equal))
        (deliverable-estimates (make-hash-table :test 'equal)))
    (dolist (group nonstarter-category-groups)
      (let* ((group-key (car group))
             (members (cdr group))
             (group-est (cdr (assoc group-key nonstarter-quadrant-mana-estimates)))
             (leaves '()))
        (dolist (cat members)
          (let ((items (gethash cat deliverables-by-category))
                (subitems (cdr (assoc cat nonstarter-category-subitems))))
            (cond
             ((and items (> (length items) 0))
              (dolist (item items)
                (push (list :kind 'deliverable :item item) leaves)))
             ((and subitems (> (length subitems) 0))
              (dolist (sub subitems)
                (push (list :kind 'subitem :cat cat :name sub) leaves)))
             (t
              (push (list :kind 'category :cat cat) leaves)))))
        (let ((count (length leaves)))
          (when (and (numberp group-est) (> count 0))
            (let ((per (/ (float group-est) count)))
              (dolist (leaf leaves)
                (pcase (plist-get leaf :kind)
                  ('category (puthash (plist-get leaf :cat) per category-estimates))
                  ('subitem (puthash (cons (plist-get leaf :cat)
                                           (plist-get leaf :name))
                                     per subitem-estimates))
                  ('deliverable (puthash (nonstarter--deliverable-key (plist-get leaf :item))
                                         per deliverable-estimates)))))))))
    (list :category category-estimates
          :subitem subitem-estimates
          :deliverable deliverable-estimates)))

(defun nonstarter--format-deliverable-line (item awarded &optional indent title estimate)
  (let* ((title (or title (alist-get 'title item) "?"))
         (due (alist-get 'due item))
         (status (alist-get 'status item))
         (mana (nonstarter--deliverable-mana item))
         (mana-str (cond
                    ((numberp mana) (format "%.1f" mana))
                    ((numberp estimate)
                     (propertize (format "%.1f" estimate)
                                 'face 'nonstarter-mana-flow-face))
                    (t "?")))
         (votes (nonstarter--deliverable-votes item))
         (tag (nonstarter--deliverable-tag-with-estimate item awarded estimate))
         (meta-parts (delq nil (list tag
                                    (when nonstarter-show-deliverable-mana
                                      (if (alist-get 'hold item)
                                          (format "mana %s, fiat 1"
                                                  mana-str)
                                        (format "mana %s, votes %s"
                                                mana-str
                                                (format "%.0f" votes)))))))
         (meta (if meta-parts (format " [%s]" (string-join meta-parts "; ")) ""))
         (tail (if nonstarter-show-deliverable-meta
                   (concat (if due (format " (due %s)" due) "")
                           (if status (format " [%s]" status) ""))
                 "")))
    (format "%s- %s%s%s\n" (or indent "") title meta tail)))

(defun nonstarter--funded-item-p (item awarded)
  (or (alist-get 'hold item)
      (and awarded (gethash (alist-get 'id item) awarded))))

(defun nonstarter--funded-items (items awarded)
  (let ((hold-items (seq-filter (lambda (it) (alist-get 'hold it)) items))
        (awarded-items (seq-filter (lambda (it)
                                     (and (not (alist-get 'hold it))
                                          (gethash (alist-get 'id it) awarded)))
                                   items)))
    (append hold-items (nonstarter--order-deliverables awarded-items))))

(defun nonstarter--deliverable-done-p (item)
  (let ((status (downcase (format "%s" (alist-get 'status item)))))
    (member status '("done" "complete" "closed"))))

(defun nonstarter--deliverables-by-category (items)
  (let ((acc (make-hash-table :test 'equal)))
    (dolist (item items)
      (unless (nonstarter--deliverable-done-p item)
        (let* ((area (alist-get 'area item))
               (explicit (alist-get 'category item))
               (cat (or (and explicit (format "%s" explicit))
                        (cdr (assoc area nonstarter-deliverable-category-map))
                        (cdr (assoc (format "%s" area) nonstarter-deliverable-category-map)))))
          (when cat
            (push item (gethash cat acc))))))
    (maphash (lambda (k v)
               (puthash k (nreverse v) acc))
             acc)
    acc))

(defun nonstarter--format-map (label alist &optional color-map)
  (concat label ": "
          (if (and alist (listp alist))
              (mapconcat (lambda (pair)
                           (let* ((key (format "%s" (car pair)))
                                  (key-display (if color-map
                                                   (nonstarter--propertize-category key color-map)
                                                 key)))
                             (concat key-display "=" (format "%s" (cdr pair)))))
                         alist ", ")
            "none")))

(defun nonstarter--format-money (amount)
  (if (numberp amount)
      (format "%.2f" amount)
    "n/a"))

(defun nonstarter--format-hours (hours)
  (if (numberp hours)
      (format "%.1f" hours)
    "n/a"))

(defun nonstarter--format-amount (value)
  (if (numberp value)
      (if (= value (floor (float value)))
          (format "%.0f" (float value))
        (format "%.1f" (float value)))
    "n/a"))

(defvar nonstarter-dashboard-mode-map
  (let ((map (make-sparse-keymap)))
    (define-key map (kbd "g") #'nonstarter-dashboard)
    (define-key map (kbd "b") #'nonstarter-personal-bid)
    (define-key map (kbd "B") #'nonstarter-personal-bid-form)
    (define-key map (kbd "c") #'nonstarter-personal-clear)
    (define-key map (kbd "v") #'nonstarter-personal-verdict)
    (define-key map (kbd "e") #'nonstarter-personal-epoch-create)
    (define-key map (kbd "E") #'nonstarter-personal-epochs)
    (define-key map (kbd "M-e") #'nonstarter-personal-epoch-update)
    (define-key map (kbd "n") #'nonstarter-personal-engagement-create)
    (define-key map (kbd "i") #'nonstarter-personal-engagement-item-create)
    (define-key map (kbd "u") #'nonstarter-personal-engagement-update)
    (define-key map (kbd "I") #'nonstarter-personal-engagement-items)
    (define-key map (kbd "h") #'nonstarter-personal-goal-create)
    (define-key map (kbd "H") #'nonstarter-personal-goals)
    (define-key map (kbd "M-h") #'nonstarter-personal-goal-update)
    (define-key map (kbd "d") #'nonstarter-personal-apply-default-bids)
    (define-key map (kbd "t") #'nonstarter-toggle-deliverable-meta)
    (define-key map (kbd "p") #'nonstarter-portfolio-step)
    (define-key map (kbd "P") #'nonstarter-portfolio-bid-form)
    (define-key map (kbd "C") #'nonstarter-portfolio-clear-form)
    map)
  "Keymap for nonstarter dashboard.")

(define-derived-mode nonstarter-dashboard-mode special-mode "Nonstarter"
  "Dashboard for Nonstarter.")

(defun nonstarter-toggle-average-day ()
  "Toggle the average-day SVG display in the dashboard."
  (interactive)
  (setq nonstarter-show-average-day (not nonstarter-show-average-day))
  (message "Average day %s" (if nonstarter-show-average-day "shown" "hidden"))
  (nonstarter-dashboard))

(defun nonstarter-toggle-deliverable-meta ()
  "Toggle deliverable due/status display in the dashboard."
  (interactive)
  (setq nonstarter-show-deliverable-meta (not nonstarter-show-deliverable-meta))
  (message "Deliverable metadata %s"
           (if nonstarter-show-deliverable-meta "shown" "hidden"))
  (nonstarter-dashboard))

(defun nonstarter-dashboard ()
  "Show a dashboard for the current personal week."
  (interactive)
  (nonstarter--maybe-start-personal-server)
  (let* ((status (nonstarter--request "GET" "/api/personal/status"))
         (week (nonstarter--request "GET" "/api/personal/week"))
         (epoch (nonstarter--request "GET" "/api/personal/epoch"))
         (history (nonstarter--request "GET" "/api/personal/history?n=6"))
         (mana (nonstarter--request "GET" "/api/personal/mana"))
         (dash (nonstarter--request "GET" "/api/personal/dashboard"))
         (svg (alist-get 'svg dash))
         (bids (alist-get 'bids week))
         (hold (nonstarter--hold-from-bids bids))
         (epoch-meta (alist-get 'meta epoch))
         (raw-budgets (and (listp epoch-meta)
                           (or (alist-get 'category-budgets epoch-meta)
                               (alist-get 'category_budgets epoch-meta))))
         (category-budgets (nonstarter--normalize-budget-map raw-budgets))
         (epoch-weeks (let ((w (alist-get 'weeks epoch)))
                        (when (numberp w) w)))
         (hold-items (nonstarter--hold-items hold (alist-get 'week-id status)))
         (items (append hold-items (alist-get 'outstanding dash)))
         (use-budgets (and category-budgets (> (length category-budgets) 0)))
         (awards (if use-budgets
                     (nonstarter--award-deliverables-by-category items category-budgets)
                   (nonstarter--award-deliverables items (alist-get 'balance mana))))
         (awarded (car awards))
         (remaining (cadr awards))
         (remaining-total (nonstarter--remaining-total remaining))
         (funded-items (nonstarter--funded-items items awarded))
         (backlog-items (seq-remove (lambda (it) (nonstarter--funded-item-p it awarded)) items))
         (svg-body (when (and (stringp svg) (not (string= svg "")))
                     (nonstarter--request-body "GET" svg
                                               nil
                                               '(("Accept" . "image/svg+xml")))))
         (category-colors (nonstarter--svg-legend-colors svg-body)))
    (let ((buf (get-buffer-create "*Nonstarter Dashboard*")))
      (with-current-buffer buf
        (let ((inhibit-read-only t))
          (read-only-mode -1)
          (erase-buffer)
          (insert "Nonstarter Dashboard\n\n")
          (insert (nonstarter--format-kv "Base URL" nonstarter-base-url) "\n")
          (insert (nonstarter--format-kv "Week" (alist-get 'week-id status)) "\n")
          (let* ((bid-total (alist-get 'bid-total status))
                 (burn (nonstarter--hold-total hold))
                 (discretionary (when (numberp bid-total) (- bid-total burn)))
                 (sleep-hold (nonstarter--hold-amount hold "sleep"))
                 (maintenance-hold (nonstarter--hold-amount hold "maintenance")))
            (insert (nonstarter--format-kv "Bid total" bid-total) "\n")
            (when (and (numberp burn) (> burn 0))
              (insert (format "Biocompatibility burn: %.1f%s\n"
                              burn
                              (if (numberp discretionary)
                                  (format " (discretionary %.1f)" discretionary)
                                "")))))
          (insert (nonstarter--format-kv "Clear total" (alist-get 'clear-total status)) "\n")
          (insert (nonstarter--format-kv "Unallocated" (alist-get 'unallocated status)) "\n")
          (when (and mana (listp mana))
            (insert (format "🔮 Mana (week %s): balance %s (earned %s / spent %s / budget %s, remaining %s)\n"
                            (or (alist-get 'week-id mana) "?")
                            (nonstarter--format-hours (alist-get 'balance mana))
                            (nonstarter--format-hours (alist-get 'earned mana))
                            (nonstarter--format-hours (alist-get 'spent mana))
                            (nonstarter--format-hours (alist-get 'budget mana))
                            (nonstarter--format-hours remaining-total))))
          (insert "\n")
          (insert "Epoch\n")
          (if (and epoch (listp epoch) (alist-get 'start-week epoch))
              (progn
                (insert (nonstarter--format-kv "Name" (alist-get 'name epoch)) "\n")
                (insert (nonstarter--format-kv "Start week" (alist-get 'start-week epoch)) "\n")
                (insert (nonstarter--format-kv "Weeks" (alist-get 'weeks epoch)) "\n")
                (insert (nonstarter--format-kv "Week index" (alist-get 'week-index epoch)) "\n")
                (insert (nonstarter--format-map "Epoch bids" (alist-get 'bids epoch) category-colors) "\n")
                (insert (nonstarter--format-map "Epoch meta" (alist-get 'meta epoch)) "\n")
                (let* ((meta (alist-get 'meta epoch))
                       (hours-week (alist-get 'hours-week meta))
                       (util-max (or (alist-get 'utilization-max meta)
                                     (alist-get 'utilization meta))))
                  (when (and (numberp hours-week) (numberp util-max))
                    (let* ((effective (* hours-week util-max))
                           (slack (- hours-week effective)))
                      (insert (format "Observed work cap: %.1f h/week (slack %.1f h)\n"
                                      effective slack)))))
                (insert "\n"))
            (insert "No epoch configured.\n\n"))
          (insert "Default bids\n")
          (insert (nonstarter--format-map "Bids" (alist-get 'bids week) category-colors) "\n")
          (when (seq hold)
            (insert (nonstarter--format-map "Hold" hold category-colors) "\n"))
          (insert (nonstarter--format-map "Clears" (alist-get 'clears week) category-colors) "\n\n")
          (insert "Average day ")
          (insert-text-button (if nonstarter-show-average-day "[hide]" "[show]")
                              'follow-link t
                              'help-echo "Toggle average-day SVG"
                              'action (lambda (_btn) (nonstarter-toggle-average-day)))
          (insert "\n")
          (when (and nonstarter-show-average-day svg-body (image-type-available-p 'svg))
            (let ((img (create-image svg-body 'svg t)))
              (when img
                (insert-image img)
                (insert "\n"))))
          (when (and (stringp svg) (not (string= svg "")))
            (insert (nonstarter--format-kv "SVG" svg) "\n"))
          (insert "\n")
          (insert "Funded\n")
          (if (and funded-items (> (length funded-items) 0))
              (dolist (item funded-items)
                (let* ((cat (alist-get 'category item))
                       (title (alist-get 'title item))
                       (hold-weight (and (alist-get 'hold item)
                                         (nonstarter--hold-weight cat)))
                       (label (cond
                               ((and cat hold-weight)
                                (concat (nonstarter--propertize-category cat category-colors)
                                        (format "(%.0f%%)" (* 100.0 hold-weight))))
                               ((and cat title)
                                (concat (nonstarter--propertize-category cat category-colors)
                                        ": "
                                        title))
                               (title title)
                               (t "?"))))
                  (insert (nonstarter--format-deliverable-line item awarded "  " label))))
            (insert "  none\n"))
          (insert "──────────────────── funded above / backlog below ────────────────────\n\n")
          (insert "Categories\n")
          (if nonstarter-category-descriptions
              (let* ((used (make-hash-table :test 'equal))
                     (others '())
                     (deliverables-by-category (nonstarter--deliverables-by-category backlog-items))
                     (leaf-estimates (nonstarter--compute-leaf-estimates deliverables-by-category))
                     (category-estimates (plist-get leaf-estimates :category))
                     (subitem-estimates (plist-get leaf-estimates :subitem))
                     (deliverable-estimates (plist-get leaf-estimates :deliverable)))
                (dolist (group nonstarter-category-groups)
                  (let* ((key (car group))
                         (members (cdr group))
                         (desc (cdr (assoc key nonstarter-category-descriptions))))
                    (puthash key t used)
                    (let* ((estimate (cdr (assoc key nonstarter-quadrant-mana-estimates)))
                           (estimate-note (nonstarter--format-estimate-note estimate)))
                      (insert (format "%s%s%s\n"
                                      (nonstarter--propertize-category key category-colors (upcase key))
                                      (if desc (format ": %s" desc) "")
                                      (or estimate-note ""))))
                    (dolist (cat members)
                      (let ((cdesc (cdr (assoc cat nonstarter-category-descriptions))))
                        (when cdesc
                          (puthash cat t used)
                          (let ((note (nonstarter--category-funding-note cat hold epoch-weeks))
                                (budget-note (when use-budgets
                                               (nonstarter--category-budget-note cat category-budgets remaining)))
                                (estimate-note (nonstarter--format-estimate-note
                                                (and category-estimates
                                                     (gethash cat category-estimates))
                                                'nonstarter-mana-flow-face)))
                            (insert (format "  - %s: %s%s\n"
                                            (nonstarter--propertize-category cat category-colors)
                                            cdesc
                                            (concat (or note "") (or budget-note "") (or estimate-note "")))))
                          (let ((subitems (cdr (assoc cat nonstarter-category-subitems))))
                            (dolist (sub subitems)
                              (let* ((label (if (consp sub) (car sub) sub))
                                     (desc (if (consp sub) (cdr sub) nil))
                                     (estimate (and subitem-estimates
                                                    (gethash (cons cat label) subitem-estimates)))
                                     (estimate-note (nonstarter--format-estimate-note
                                                     estimate
                                                     'nonstarter-mana-flow-face)))
                                (insert (format "    - %s%s%s\n"
                                                label
                                                (if (and desc (not (string= desc "")))
                                                    (format ": %s" desc)
                                                  "")
                                                (or estimate-note ""))))))
                          (dolist (item (gethash cat deliverables-by-category))
                            (let ((estimate (and deliverable-estimates
                                                 (gethash (nonstarter--deliverable-key item)
                                                          deliverable-estimates))))
                              (insert (nonstarter--format-deliverable-line item awarded "    " nil estimate))))))))))
                (dolist (entry nonstarter-category-descriptions)
                  (unless (gethash (car entry) used)
                    (push entry others)))
                (setq others (nreverse others))
                (when others
                  (dolist (entry others)
                    (let ((cat (car entry)))
                      (let ((note (nonstarter--category-funding-note cat hold epoch-weeks))
                            (budget-note (when use-budgets
                                           (nonstarter--category-budget-note cat category-budgets remaining)))
                            (estimate-note (nonstarter--format-estimate-note
                                            (and category-estimates
                                                 (gethash cat category-estimates))
                                            'nonstarter-mana-flow-face)))
                        (insert (format "- %s: %s%s\n"
                                        (nonstarter--propertize-category cat category-colors)
                                        (cdr entry)
                                        (concat (or note "") (or budget-note "") (or estimate-note "")))))
                      (dolist (item (gethash cat deliverables-by-category))
                        (let ((estimate (and deliverable-estimates
                                             (gethash (nonstarter--deliverable-key item)
                                                      deliverable-estimates))))
                          (insert (nonstarter--format-deliverable-line item awarded "    " nil estimate)))))))
                (insert "\n"))
            (insert "No category descriptions configured.\n"))
          (insert "\n")
          (insert "Engagements (Q2)\n")
          (let ((engagements (nonstarter--request "GET" "/api/personal/engagements")))
            (if (and engagements (listp engagements))
                (let ((any nil))
                  (dolist (eng engagements)
                    (when (string= (alist-get 'category eng) "q2")
                      (setq any t)
                      (let* ((name (alist-get 'name eng))
                             (eid (alist-get 'id eng))
                             (rate (alist-get 'rate_hour eng))
                             (cap (alist-get 'budget_cap eng))
                             (billed (alist-get 'billed_total eng))
                             (remaining (when (and (numberp cap) (numberp billed)) (- cap billed)))
                             (hours-remaining (when (and (numberp remaining) (numberp rate) (not (= rate 0)))
                                                (/ remaining rate))))
                        (insert (format "- %s (rate %s, cap %s, billed %s)\n"
                                        name
                                        (nonstarter--format-money rate)
                                        (nonstarter--format-money cap)
                                        (nonstarter--format-money billed)))
                        (when remaining
                          (insert (format "  remaining %s (~%s h)\n"
                                          (nonstarter--format-money remaining)
                                          (nonstarter--format-hours hours-remaining))))
                        (when eid
                          (let ((items (nonstarter--request "GET"
                                                            (concat "/api/personal/engagement/items?id=" eid))))
                            (when (and items (listp items))
                              (let ((count (length items)))
                                (insert (format "  items: %d\n" count)))
                              (dolist (item items)
                                (let ((title (alist-get 'title item))
                                      (status (alist-get 'status item))
                                      (hours (alist-get 'hours item)))
                                  (insert (format "    - %s [%s%s]\n"
                                                  title
                                                  (or status "unknown")
                                                  (if (numberp hours)
                                                      (format ", %s h" (nonstarter--format-hours hours))
                                                    "")))))))))))
                  (unless any
                    (insert "No Q2 engagements.\n")))
              (insert "No engagements.\n")))
          (insert "\n")
          (insert "Goals\n")
          (let ((goals (nonstarter--request "GET" "/api/personal/goals")))
            (if (and goals (listp goals) (> (length goals) 0))
                (dolist (goal goals)
                  (insert (format "- %s [%s] %s/%s\n"
                                  (alist-get 'title goal)
                                  (alist-get 'status goal)
                                  (nonstarter--format-hours (alist-get 'actual_count goal))
                                  (nonstarter--format-hours (alist-get 'target_count goal)))))
              (insert "No goals.\n")))
          (insert "\n")
          (insert "Recent verdicts\n")
          (if (and history (listp history))
              (dolist (entry history)
                (insert (format "- %s %s (bid %.1f / clear %.1f)\n"
                                (alist-get 'week-id entry)
                                (alist-get 'verdict entry)
                                (alist-get 'bid-total entry)
                                (alist-get 'clear-total entry))))
            (insert "No history.\n"))
          ;; Portfolio section (fetches from futon5 heartbeat + futon3c AIF)
          (nonstarter--dashboard-insert-portfolio)
          (goto-char (point-min))
          (nonstarter-dashboard-mode))
      (display-buffer buf))))

(defun nonstarter--dashboard-insert-portfolio ()
  "Insert the portfolio section into the dashboard buffer.
Fetches from futon5 (heartbeat) and futon3c (AIF state).
Silently skips if both servers are unreachable."
  (let* ((heartbeat (nonstarter--futon5-request "GET" "/api/heartbeat"))
         (aif-state (nonstarter--futon3c-request "GET" "/api/alpha/portfolio/state")))
    (when (or heartbeat aif-state)
      (insert "\nPortfolio\n")
      ;; AIF state
      (if (and aif-state (alist-get 'ok aif-state))
          (let* ((state (alist-get 'state aif-state))
                 (mode (alist-get 'mode state))
                 (urgency (alist-get 'urgency state))
                 (step-count (alist-get 'step-count state)))
            (insert (format "  AIF: mode %s | urgency %.2f | steps %s\n"
                            (or mode "?")
                            (or urgency 0.0)
                            (or step-count "?"))))
        (insert "  AIF: unavailable\n"))
      ;; Heartbeat bids
      (if (and heartbeat (not (alist-get 'error heartbeat)))
          (let* ((week-id (alist-get 'week_id heartbeat))
                 (bids (alist-get 'bids heartbeat))
                 (clears (alist-get 'clears heartbeat))
                 (mode-pred (alist-get 'mode_prediction heartbeat))
                 (mode-obs (alist-get 'mode_observed heartbeat)))
            (insert (format "  Heartbeat: %s\n" (or week-id "?")))
            (if bids
                (progn
                  (insert (format "  Bids (%d):" (length bids)))
                  (when mode-pred (insert (format " [predicted: %s]" mode-pred)))
                  (insert "\n")
                  (dolist (bid bids)
                    (insert (format "    - %s %s [%s]\n"
                                    (or (alist-get 'action bid) "?")
                                    (or (alist-get 'mission bid) "")
                                    (or (alist-get 'effort bid) "?")))))
              (insert "  Bids: none\n"))
            (if clears
                (progn
                  (insert (format "  Clears (%d):" (length clears)))
                  (when mode-obs (insert (format " [observed: %s]" mode-obs)))
                  (insert "\n")
                  (dolist (clear clears)
                    (insert (format "    - %s %s [%s] => %s\n"
                                    (or (alist-get 'action clear) "?")
                                    (or (alist-get 'mission clear) "")
                                    (or (alist-get 'effort clear) "?")
                                    (or (alist-get 'outcome clear) "?")))))
              (insert "  Clears: none\n")))
        (insert "  Heartbeat: no data for current week\n"))
      (insert "\n"))))

;;; Public API

(defun nonstarter-public-propose (title ask &optional description sigil proposer)
  "Create a public proposal."
  (interactive
   (list (read-string "Title: ")
         (read-number "Ask: ")
         (read-string "Description (optional): " nil nil "")
         (read-string "Sigil (optional): " nil nil "")
         (read-string "Proposer (optional): " nil nil "")))
  (let ((payload `((title . ,title)
                   (ask . ,ask)
                   (description . ,(unless (string= description "") description))
                   (sigil . ,(unless (string= sigil "") sigil))
                   (proposer . ,(unless (string= proposer "") proposer)))))
    (nonstarter--show "Public propose"
                      (nonstarter--request "POST" "/api/public/propose" payload))))

(defun nonstarter-public-donate (amount &optional donor note)
  "Donate to the public pool."
  (interactive
   (list (read-number "Amount: ")
         (read-string "Donor (optional): " nil nil "")
         (read-string "Note (optional): " nil nil "")))
  (let ((payload `((amount . ,amount)
                   (donor . ,(unless (string= donor "") donor))
                   (note . ,(unless (string= note "") note)))))
    (nonstarter--show "Public donate"
                      (nonstarter--request "POST" "/api/public/donate" payload))))

(defun nonstarter-public-vote (proposal-id &optional voter weight)
  "Vote on a public proposal."
  (interactive
   (list (read-string "Proposal ID: ")
         (read-string "Voter (optional): " nil nil "")
         (read-number "Weight: " 1)))
  (let ((payload `((proposal-id . ,proposal-id)
                   (voter . ,(unless (string= voter "") voter))
                   (weight . ,weight))))
    (nonstarter--show "Public vote"
                      (nonstarter--request "POST" "/api/public/vote" payload))))

(defun nonstarter-public-check (&optional threshold)
  "Check thresholds and auto-fund eligible proposals."
  (interactive (list (read-number "Threshold: " 10)))
  (let ((payload `((threshold . ,threshold))))
    (nonstarter--show "Public check"
                      (nonstarter--request "POST" "/api/public/check" payload))))

(defun nonstarter-public-proposals (&optional status)
  "List public proposals (optionally filtered by status)."
  (interactive (list (read-string "Status (blank for all): " nil nil "")))
  (nonstarter--show "Public proposals"
                    (nonstarter--request "GET"
                                         (if (string= status "")
                                             "/api/public/proposals"
                                           (concat "/api/public/proposals?status=" status)))))

(defun nonstarter-public-proposal (proposal-id)
  "Get a single public proposal."
  (interactive (list (read-string "Proposal ID: ")))
  (nonstarter--show "Public proposal"
                    (nonstarter--request "GET"
                                         (concat "/api/public/proposal?id=" proposal-id))))

(defun nonstarter-public-pool ()
  "Get public pool stats."
  (interactive)
  (nonstarter--show "Public pool"
                    (nonstarter--request "GET" "/api/public/pool")))

(defun nonstarter-public-history ()
  "Get public funding history."
  (interactive)
  (nonstarter--show "Public history"
                    (nonstarter--request "GET" "/api/public/history")))

;;; Personal API

(defun nonstarter-personal-bid (category hours &optional week-id)
  "Record a personal bid."
  (interactive
   (list (intern (read-string "Category: " "q4"))
         (read-number "Hours: ")
         (read-string "Week ID (YYYY-MM-DD, optional): " nil nil "")))
  (let ((payload `((category . ,(symbol-name category))
                   (hours . ,hours)
                   (week-id . ,(unless (string= week-id "") week-id)))))
    (nonstarter--show "Personal bid"
                      (nonstarter--request "POST" "/api/personal/bid" payload))))

(defun nonstarter-personal-bid-form (&optional week-id)
  "Prompt for weekly bids using the category tree."
  (interactive (list (read-string "Week ID (YYYY-MM-DD, optional): " nil nil "")))
  (let* ((week-id (unless (string= week-id "") week-id))
         (status (nonstarter--request "GET" "/api/personal/status"))
         (week (nonstarter--request "GET"
                                    (if week-id
                                        (concat "/api/personal/week?week-id=" week-id)
                                      "/api/personal/week")))
         (dash (nonstarter--request "GET" "/api/personal/dashboard"))
         (deliverables-by-category
          (nonstarter--deliverables-by-category (alist-get 'outstanding dash)))
         (bids (alist-get 'bids week))
         (totals (make-hash-table :test 'equal))
         (used (make-hash-table :test 'equal))
         (prompt-week (or week-id (alist-get 'week-id status))))
    (dolist (group nonstarter-category-groups)
      (let* ((group-key (car group))
             (members (cdr group)))
        (dolist (cat members)
          (puthash cat t used)
          (let* ((desc (cdr (assoc cat nonstarter-category-descriptions)))
                 (items (gethash cat deliverables-by-category))
                 (subitems (cdr (assoc cat nonstarter-category-subitems)))
                 (base-label (format "%s / %s%s"
                                     (upcase group-key)
                                     cat
                                     (if desc (format " (%s)" desc) ""))))
            (cond
             ((and items (> (length items) 0))
              (dolist (item items)
                (let* ((title (alist-get 'title item))
                       (due (alist-get 'due item))
                       (leaf-label (format "%s / %s%s"
                                           base-label
                                           title
                                           (if due (format " (due %s)" due) "")))
                       (hours (nonstarter--read-hours (concat leaf-label " hours: ")
                                                      nil t)))
                  (when (numberp hours)
                    (nonstarter--add-total totals cat hours)))))
             ((and subitems (> (length subitems) 0))
              (dolist (sub subitems)
                (let* ((label (if (consp sub) (car sub) sub))
                       (desc* (if (consp sub) (cdr sub) nil))
                       (leaf-label (format "%s / %s%s"
                                           base-label
                                           label
                                           (if desc* (format " (%s)" desc*) "")))
                       (hours (nonstarter--read-hours (concat leaf-label " hours: ")
                                                      nil t)))
                  (when (numberp hours)
                    (nonstarter--add-total totals cat hours)))))
             (t
              (let* ((current (nonstarter--parse-number
                               (alist-get cat bids nil nil #'string=)))
                     (hours (nonstarter--read-hours (concat base-label " hours: ")
                                                    current nil)))
                (when (numberp hours)
                  (puthash cat hours totals)))))))))
    (dolist (entry nonstarter-category-descriptions)
      (let ((cat (car entry)))
        (unless (gethash cat used)
          (let* ((desc (cdr entry))
                 (current (nonstarter--parse-number
                           (alist-get cat bids nil nil #'string=)))
                 (label (format "Other / %s%s"
                                cat
                                (if desc (format " (%s)" desc) "")))
                 (hours (nonstarter--read-hours (concat label " hours: ")
                                                current nil)))
            (when (numberp hours)
              (puthash cat hours totals)))))))
    (let ((summary
           (mapconcat
            (lambda (cat)
              (let ((hours (gethash cat totals)))
                (format "- %s: %.1f" cat (or hours 0.0))))
            (sort (hash-table-keys totals) #'string<)
            "\n")))
      (nonstarter--show-text "Bid form summary"
                             (format "Week: %s\n\n%s\n" (or prompt-week "?") summary))
      (when (y-or-n-p "Submit these bids? ")
        (maphash
         (lambda (cat hours)
           (when (numberp hours)
             (nonstarter--request "POST" "/api/personal/bid"
                                  `((category . ,cat)
                                    (hours . ,hours)
                                    (week-id . ,(unless (null week-id) week-id))))))
         totals)
        ;; T-8 compression: optionally emit portfolio bids from personal bids
        (when (and nonstarter-portfolio-category-mission-map
                   (y-or-n-p "Also emit portfolio bids (effort bands)? "))
          (let ((portfolio-bids (nonstarter--compress-bids-to-portfolio totals)))
            (when portfolio-bids
              (let* ((mode (completing-read "Mode prediction: "
                                           nonstarter-portfolio-modes nil t nil nil "BUILD"))
                     (payload `((week-id . ,(unless (null week-id) week-id))
                                (bids . ,(apply #'vector portfolio-bids))
                                (mode-prediction . ,mode)))
                     (result (nonstarter--futon5-request "POST" "/api/heartbeat/bid" payload)))
                (if result
                    (message "Portfolio bids also recorded (%d entries)." (length portfolio-bids))
                  (message "Warning: futon5 heartbeat API unreachable."))))))
        (nonstarter-dashboard))))

(defun nonstarter-personal-clear (category hours &optional week-id)
  "Record a personal clear."
  (interactive
   (list (intern (read-string "Category: " "q4"))
         (read-number "Hours: ")
         (read-string "Week ID (YYYY-MM-DD, optional): " nil nil "")))
  (let ((payload `((category . ,(symbol-name category))
                   (hours . ,hours)
                   (week-id . ,(unless (string= week-id "") week-id)))))
    (nonstarter--show "Personal clear"
                      (nonstarter--request "POST" "/api/personal/clear" payload))))

;;; Portfolio API

(defun nonstarter--portfolio-read-effort (prompt)
  "Prompt for an effort band. Returns a string."
  (completing-read prompt '("trivial" "easy" "medium" "hard" "epic") nil t))

(defun nonstarter--portfolio-read-action (prompt)
  "Prompt for a portfolio action. Returns a string."
  (completing-read prompt nonstarter-portfolio-actions nil t nil nil "work-on"))

(defun nonstarter--portfolio-read-outcome (prompt)
  "Prompt for a clear outcome. Returns a string."
  (completing-read prompt nonstarter-portfolio-outcomes nil t nil nil "complete"))

(defun nonstarter--portfolio-read-mission (prompt)
  "Prompt for a mission ID. Uses `nonstarter-portfolio-missions' or fetches from futon3c."
  (let ((missions (or nonstarter-portfolio-missions
                      (nonstarter--fetch-mission-list))))
    (if missions
        (completing-read prompt (mapcar #'car missions) nil nil)
      (read-string prompt))))

(defun nonstarter--fetch-mission-list ()
  "Fetch the mission list from futon3c. Returns (ID . DESCRIPTION) alist or nil."
  (let ((resp (nonstarter--futon3c-request "GET" "/api/alpha/missions")))
    (when (and resp (listp resp) (alist-get 'ok resp))
      (mapcar (lambda (m)
                (cons (or (alist-get 'id m) (format "%s" m))
                      (or (alist-get 'title m) "")))
              (alist-get 'missions resp)))))

(defun nonstarter-portfolio-bid-form (&optional week-id)
  "Prompt for portfolio bids: mission/action/effort triples.
Posts to futon5 /api/heartbeat/bid for persistence."
  (interactive (list (read-string "Week ID (YYYY-Www, optional): " nil nil "")))
  (let* ((week-id (unless (string= week-id "") week-id))
         (bids nil)
         (continue t))
    (while continue
      (let* ((mission (nonstarter--portfolio-read-mission "Mission: "))
             (action (nonstarter--portfolio-read-action "Action: "))
             (effort (nonstarter--portfolio-read-effort "Effort: ")))
        (push `((action . ,action)
                (mission . ,mission)
                (effort . ,effort))
              bids))
      (setq continue (y-or-n-p "Add another bid? ")))
    (setq bids (nreverse bids))
    (let ((mode (completing-read "Mode prediction: "
                                 nonstarter-portfolio-modes nil t nil nil "BUILD")))
      (nonstarter--show-text
       "Portfolio bid summary"
       (format "Week: %s\nMode: %s\n\n%s"
               (or week-id "current")
               mode
               (mapconcat
                (lambda (bid)
                  (format "- %s %s [%s]"
                          (alist-get 'action bid)
                          (alist-get 'mission bid)
                          (alist-get 'effort bid)))
                bids "\n")))
      (when (y-or-n-p "Submit portfolio bids? ")
        (let* ((payload `((week-id . ,week-id)
                          (bids . ,(apply #'vector bids))
                          (mode-prediction . ,mode)))
               (result (nonstarter--futon5-request "POST" "/api/heartbeat/bid" payload)))
          (if result
              (progn (message "Portfolio bids recorded.")
                     (nonstarter-dashboard))
            (message "Failed to record portfolio bids (futon5 unreachable).")))))))

(defun nonstarter-portfolio-clear-form (&optional week-id)
  "Prompt for portfolio clears: mission/action/effort/outcome quads.
Pre-populates from existing bids when available."
  (interactive (list (read-string "Week ID (YYYY-Www, optional): " nil nil "")))
  (let* ((week-id (unless (string= week-id "") week-id))
         (heartbeat (nonstarter--futon5-request
                     "GET" (if week-id
                               (concat "/api/heartbeat?week-id=" week-id)
                             "/api/heartbeat")))
         (existing-bids (when (and heartbeat (not (alist-get 'error heartbeat)))
                          (alist-get 'bids heartbeat)))
         (clears nil))
    ;; Walk existing bids and ask for outcomes
    (when existing-bids
      (dolist (bid existing-bids)
        (let* ((mission (or (alist-get 'mission bid) "?"))
               (action (or (alist-get 'action bid) "work-on"))
               (bid-effort (or (alist-get 'effort bid) "medium"))
               (effort (nonstarter--portfolio-read-effort
                        (format "%s %s (bid: %s) — actual effort: " action mission bid-effort)))
               (outcome (nonstarter--portfolio-read-outcome
                         (format "%s %s — outcome: " action mission))))
          (push `((action . ,action)
                  (mission . ,mission)
                  (effort . ,effort)
                  (outcome . ,outcome))
                clears))))
    ;; Add unplanned work or free-form entries
    (while (y-or-n-p (if existing-bids "Add unplanned work? " "Add a clear entry? "))
      (let* ((mission (nonstarter--portfolio-read-mission "Mission: "))
             (action (nonstarter--portfolio-read-action "Action: "))
             (effort (nonstarter--portfolio-read-effort "Effort: "))
             (outcome (nonstarter--portfolio-read-outcome "Outcome: ")))
        (push `((action . ,action)
                (mission . ,mission)
                (effort . ,effort)
                (outcome . ,outcome))
              clears)))
    (setq clears (nreverse clears))
    (when clears
      (let ((mode (completing-read "Mode observed: "
                                   nonstarter-portfolio-modes nil t nil nil "BUILD")))
        (nonstarter--show-text
         "Portfolio clear summary"
         (format "Week: %s\nMode observed: %s\n\n%s"
                 (or week-id "current")
                 mode
                 (mapconcat
                  (lambda (c)
                    (format "- %s %s [%s] => %s"
                            (alist-get 'action c)
                            (alist-get 'mission c)
                            (alist-get 'effort c)
                            (alist-get 'outcome c)))
                  clears "\n")))
        (when (y-or-n-p "Submit portfolio clears? ")
          ;; Persist to futon5
          (let* ((payload `((week-id . ,week-id)
                            (clears . ,(apply #'vector clears))
                            (mode-observed . ,mode)))
                 (f5-result (nonstarter--futon5-request "POST" "/api/heartbeat/clear" payload)))
            (if f5-result
                (message "Portfolio clears recorded.")
              (message "Warning: futon5 unreachable, clears not persisted.")))
          ;; Run AIF analysis
          (when (y-or-n-p "Run AIF heartbeat analysis? ")
            (let* ((hb-payload `((bids . ,(when existing-bids (apply #'vector existing-bids)))
                                 (clears . ,(apply #'vector clears))
                                 (mode-prediction . ,(when heartbeat
                                                       (alist-get 'mode_prediction heartbeat)))
                                 (mode-observed . ,mode)))
                   (aif-result (nonstarter--futon3c-request
                                "POST" "/api/alpha/portfolio/heartbeat" hb-payload)))
              (if aif-result
                  (nonstarter--show-text
                   "Portfolio AIF Heartbeat"
                   (format "Recommendation: %s\n\nAction: %s\nDiagnostics: %s"
                           (or (alist-get 'recommendation aif-result) "?")
                           (or (alist-get 'action aif-result) "?")
                           (pp-to-string (alist-get 'diagnostics aif-result))))
                (message "futon3c unreachable, skipping AIF analysis."))))
          (nonstarter-dashboard))))))

(defun nonstarter-portfolio-step ()
  "Run one AIF portfolio step via futon3c and show the recommendation."
  (interactive)
  (let ((result (nonstarter--futon3c-request "POST" "/api/alpha/portfolio/step" nil)))
    (if result
        (nonstarter--show-text
         "Portfolio AIF Step"
         (format "Recommendation: %s\n\nAction: %s\nAbstain: %s\nDiagnostics:\n%s"
                 (or (alist-get 'recommendation result) "?")
                 (or (alist-get 'action result) "?")
                 (if (alist-get 'abstain result) "yes" "no")
                 (pp-to-string (alist-get 'diagnostics result))))
      (message "futon3c unreachable."))))

(defun nonstarter-personal-verdict (&optional week-id note tolerance)
  "Reconcile a week into a personal block."
  (interactive
   (list (read-string "Week ID (YYYY-MM-DD, optional): " nil nil "")
         (read-string "Note (optional): " nil nil "")
         (read-number "Tolerance (hours): " 1)))
  (let ((payload `((week-id . ,(unless (string= week-id "") week-id))
                   (note . ,(unless (string= note "") note))
                   (tolerance . ,tolerance))))
    (nonstarter--show "Personal verdict"
                      (nonstarter--request "POST" "/api/personal/verdict" payload))))

(defun nonstarter-personal-epoch-create (start-week weeks bids &optional name meta)
  "Create an epoch with amortized bids."
  (interactive
   (list (read-string "Start week (YYYY-MM-DD): ")
         (read-number "Weeks: " 32)
         (read--expression "Bids alist (e.g., '((q4 . 80) (job-search . 40))): " "")
         (read-string "Name (optional): " nil nil "")
         (read--expression "Meta alist (optional, e.g., '((salary-week . 628.74))): " "")))
  (let* ((bids-map (nonstarter--stringify-keys bids))
         (meta-map (nonstarter--stringify-keys meta))
         (payload `((start-week . ,start-week)
                    (weeks . ,weeks)
                    (bids . ,bids-map)
                    (meta . ,(when (listp meta-map) meta-map))
                    (name . ,(unless (string= name "") name)))))
    (nonstarter--show "Personal epoch"
                      (nonstarter--request "POST" "/api/personal/epoch" payload))))

(defun nonstarter-personal-epochs ()
  "List personal epochs."
  (interactive)
  (nonstarter--show "Personal epochs"
                    (nonstarter--request "GET" "/api/personal/epochs")))

(defun nonstarter-personal-epoch-update (id meta bids)
  "Update an epoch's meta and/or bids by id."
  (interactive
   (list (read-string "Epoch ID: ")
         (read--expression "Meta alist (optional): " "")
         (read--expression "Bids alist (optional): " "")))
  (let* ((meta-map (nonstarter--stringify-keys meta))
         (bids-map (nonstarter--stringify-keys bids))
         (payload `((id . ,id)
                    (meta . ,(when (listp meta-map) meta-map))
                    (bids . ,(when (listp bids-map) bids-map)))))
    (nonstarter--show "Epoch update"
                      (nonstarter--request "POST" "/api/personal/epoch/update" payload))))

(defun nonstarter-personal-engagement-create (name category client rate-hour budget-cap billed-total notes)
  "Create a personal engagement."
  (interactive
   (list (read-string "Name: ")
         (read-string "Category (e.g., q2): " "q2")
         (read-string "Client (optional): " nil nil "")
         (read-number "Rate/hour (e.g., 75): " 75)
         (read-number "Budget cap (optional, 0 if unknown): " 0)
         (read-number "Billed total (optional, 0 if none): " 0)
         (read-string "Notes (optional): " nil nil "")))
  (let ((payload `((name . ,name)
                   (category . ,(unless (string= category "") category))
                   (client . ,(unless (string= client "") client))
                   (rate-hour . ,rate-hour)
                   (budget-cap . ,(unless (= budget-cap 0) budget-cap))
                   (billed-total . ,(unless (= billed-total 0) billed-total))
                   (notes . ,(unless (string= notes "") notes)))))
    (nonstarter--show "Engagement create"
                      (nonstarter--request "POST" "/api/personal/engagement" payload))))

(defun nonstarter-personal-engagement-update (id billed-total budget-cap notes)
  "Update engagement totals."
  (interactive
   (list (read-string "Engagement ID: ")
         (read-number "Billed total (0 to skip): " 0)
         (read-number "Budget cap (0 to skip): " 0)
         (read-string "Notes (optional): " nil nil "")))
  (let ((payload `((id . ,id)
                   (billed-total . ,(unless (= billed-total 0) billed-total))
                   (budget-cap . ,(unless (= budget-cap 0) budget-cap))
                   (notes . ,(unless (string= notes "") notes)))))
    (nonstarter--show "Engagement update"
                      (nonstarter--request "POST" "/api/personal/engagement/update" payload))))

(defun nonstarter-personal-engagement-item-create (engagement-id title hours status approver notes)
  "Add an engagement item."
  (interactive
   (list (read-string "Engagement ID: ")
         (read-string "Item title: ")
         (read-number "Hours (optional, 0 if unknown): " 0)
         (read-string "Status (proposed/approved/billed): " "proposed")
         (read-string "Approver (optional): " nil nil "")
         (read-string "Notes (optional): " nil nil "")))
  (let ((payload `((engagement-id . ,engagement-id)
                   (title . ,title)
                   (hours . ,(unless (= hours 0) hours))
                   (status . ,(unless (string= status "") status))
                   (approver . ,(unless (string= approver "") approver))
                   (notes . ,(unless (string= notes "") notes)))))
    (nonstarter--show "Engagement item"
                      (nonstarter--request "POST" "/api/personal/engagement/item" payload))))

(defun nonstarter-personal-engagement-items (engagement-id)
  "List items for an engagement."
  (interactive (list (read-string "Engagement ID: ")))
  (nonstarter--show "Engagement items"
                    (nonstarter--request "GET"
                                         (concat "/api/personal/engagement/items?id=" engagement-id))))

(defun nonstarter-personal-goal-create (title category target-count actual-count notes)
  "Create a weekly goal."
  (interactive
   (list (read-string "Title: ")
         (read-string "Category (optional): " nil nil "")
         (read-number "Target count: " 1)
         (read-number "Actual count (optional, 0 if none): " 0)
         (read-string "Notes (optional): " nil nil "")))
  (let ((payload `((title . ,title)
                   (category . ,(unless (string= category "") category))
                   (target-count . ,target-count)
                   (actual-count . ,(unless (= actual-count 0) actual-count))
                   (notes . ,(unless (string= notes "") notes)))))
    (nonstarter--show "Goal create"
                      (nonstarter--request "POST" "/api/personal/goal" payload))))

(defun nonstarter-personal-goal-update (id actual-count target-count notes)
  "Update a goal."
  (interactive
   (list (read-string "Goal ID: ")
         (read-number "Actual count (0 to skip): " 0)
         (read-number "Target count (0 to skip): " 0)
         (read-string "Notes (optional): " nil nil "")))
  (let ((payload `((id . ,id)
                   (actual-count . ,(unless (= actual-count 0) actual-count))
                   (target-count . ,(unless (= target-count 0) target-count))
                   (notes . ,(unless (string= notes "") notes)))))
    (nonstarter--show "Goal update"
                      (nonstarter--request "POST" "/api/personal/goal/update" payload))))

(defun nonstarter-personal-goals (&optional week-id)
  "List weekly goals."
  (interactive (list (read-string "Week ID (YYYY-MM-DD, optional): " nil nil "")))
  (nonstarter--show "Goals"
                    (nonstarter--request "GET"
                                         (if (string= week-id "")
                                             "/api/personal/goals"
                                           (concat "/api/personal/goals?week-id=" week-id)))))

(defun nonstarter-personal-apply-default-bids ()
  "Apply default weekly bids."
  (interactive)
  (dolist (entry nonstarter-default-weekly-bids)
    (let ((category (car entry))
          (hours (cdr entry)))
      (nonstarter--request "POST" "/api/personal/bid"
                           `((category . ,category)
                             (hours . ,hours)))))
  (nonstarter-dashboard))

(defun nonstarter-personal-week (&optional week-id)
  "Fetch bids/clears for a week."
  (interactive (list (read-string "Week ID (YYYY-MM-DD, optional): " nil nil "")))
  (nonstarter--show "Personal week"
                    (nonstarter--request "GET"
                                         (if (string= week-id "")
                                             "/api/personal/week"
                                           (concat "/api/personal/week?week-id=" week-id)))))

(defun nonstarter-personal-status (&optional week-id)
  "Fetch status for a week."
  (interactive (list (read-string "Week ID (YYYY-MM-DD, optional): " nil nil "")))
  (nonstarter--show "Personal status"
                    (nonstarter--request "GET"
                                         (if (string= week-id "")
                                             "/api/personal/status"
                                           (concat "/api/personal/status?week-id=" week-id)))))

(defun nonstarter-personal-history (&optional n)
  "Fetch recent personal blocks."
  (interactive (list (read-number "Weeks: " 4)))
  (nonstarter--show "Personal history"
                    (nonstarter--request "GET"
                                         (concat "/api/personal/history?n=" (number-to-string n)))))

(provide 'nonstarter)
