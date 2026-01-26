;;; nonstarter.el --- Nonstarter Emacs client -*- lexical-binding: t; -*-

(defgroup nonstarter nil
  "Emacs client for the Nonstarter HTTP API and local DB prototype."
  :group 'applications)

(require 'json)
(require 'pp)
(require 'url)

(defcustom nonstarter-base-url "http://127.0.0.1:7778"
  "Base URL for the Nonstarter server."
  :type 'string
  :group 'nonstarter)

(defcustom nonstarter-timeout 10
  "Timeout in seconds for HTTP requests."
  :type 'integer
  :group 'nonstarter)

(defcustom nonstarter-category-descriptions
  '(("q1" . "Keep-alive / retainer")
    ("q2" . "Throughput / delivery")
    ("q3" . "Low-latency / relational")
    ("q4" . "Offline / speculative")
    ("job-search" . "Job search")
    ("meetings" . "Meetings (fixed)")
    ("lnl" . "Local Network Lead tasks")
    ("orpm" . "Open Research Project Manager tasks")
    ("creative" . "Creative practice")
    ("maintenance" . "Maintenance / life admin")
    ("sleep" . "Sleep")
    ("slack" . "Slack / buffer"))
  "Descriptions for personal categories."
  :type '(alist :key-type string :value-type string)
  :group 'nonstarter)

(defcustom nonstarter-default-weekly-bids
  '(("meetings" . 8)
    ("lnl" . 5)
    ("orpm" . 10))
  "Default weekly bids to apply."
  :type '(alist :key-type string :value-type number)
  :group 'nonstarter)

;;; ---------------------------------------------------------------------------
;;; Local prototype (futon5) helpers
;;; ---------------------------------------------------------------------------

(defcustom nonstarter-root nil
  "Path to futon5 repo. If nil, derived from nonstarter.el location or FUTON5_ROOT."
  :type '(choice (const :tag "Auto" nil) directory)
  :group 'nonstarter)

(defcustom nonstarter-db-path nil
  "Path to Nonstarter SQLite DB. If nil, uses FUTON5_NONSTARTER_DB or data/nonstarter.db."
  :type '(choice (const :tag "Auto" nil) file)
  :group 'nonstarter)

(defcustom nonstarter-cli-command "clj"
  "Command used to run local Clojure scripts."
  :type 'string
  :group 'nonstarter)

(defun nonstarter--root ()
  (expand-file-name
   (or (and (stringp nonstarter-root) (not (nonstarter--blank-p nonstarter-root)) nonstarter-root)
       (getenv "FUTON5_ROOT")
       (file-name-directory (or load-file-name buffer-file-name)))))

(defun nonstarter--db ()
  (expand-file-name
   (or (and (stringp nonstarter-db-path) (not (nonstarter--blank-p nonstarter-db-path)) nonstarter-db-path)
       (getenv "FUTON5_NONSTARTER_DB")
       (expand-file-name "data/nonstarter.db" (nonstarter--root)))))

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

(defun nonstarter--run-script (script-path ns &rest args)
  (let* ((default-directory (nonstarter--root))
         (expr (concat "(load-file \"" script-path "\") "
                       "(" ns "/-main "
                       (mapconcat (lambda (s) (format "%S" s)) args " ")
                       ")"))
         (cmd (mapconcat #'shell-quote-argument
                         (list nonstarter-cli-command "-M" "-e" expr)
                         " ")))
    (shell-command-to-string cmd)))

(defun nonstarter-hypothesis-register (title statement &optional context status)
  "Register a hypothesis in the local Nonstarter DB."
  (interactive
   (list (read-string "Title: ")
         (read-string "Statement: ")
         (read-string "Context (optional): ")
         (read-string "Status (default active): ")))
  (let* ((args (list "--db" (nonstarter--db)
                     "register"
                     "--title" title
                     "--statement" statement))
         (args (if (nonstarter--blank-p context) args (append args (list "--context" context))))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_hypothesis.clj"
                        "scripts.nonstarter-hypothesis"
                        args)))
    (nonstarter--show-text "Nonstarter Hypothesis Register" output)))

(defun nonstarter-hypothesis-update (hypothesis-id status)
  "Update hypothesis status in the local Nonstarter DB."
  (interactive
   (list (read-string "Hypothesis ID: ")
         (read-string "Status: ")))
  (let ((output (nonstarter--run-script
                 "scripts/nonstarter_hypothesis.clj"
                 "scripts.nonstarter-hypothesis"
                 "--db" (nonstarter--db)
                 "update"
                 "--id" hypothesis-id
                 "--status" status)))
    (nonstarter--show-text "Nonstarter Hypothesis Update" output)))

(defun nonstarter-hypothesis-list (&optional status)
  "List hypotheses from the local Nonstarter DB."
  (interactive (list (read-string "Status (optional): ")))
  (let* ((args (list "--db" (nonstarter--db) "list"))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_hypothesis.clj"
                        "scripts.nonstarter-hypothesis"
                        args)))
    (nonstarter--show-text "Nonstarter Hypotheses" output)))

(defun nonstarter-study-register (hypothesis-id study-name &optional design metrics seeds status results notes)
  "Register a study preregistration in the local Nonstarter DB."
  (interactive
   (list (read-string "Hypothesis ID: ")
         (read-string "Study name: ")
         (read-string "Design EDN (optional): ")
         (read-string "Metrics EDN (optional): ")
         (read-string "Seeds EDN (optional): ")
         (read-string "Status (default preregistered): ")
         (read-string "Results EDN (optional): ")
         (read-string "Notes (optional): ")))
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
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_study.clj"
                        "scripts.nonstarter-study"
                        args)))
    (nonstarter--show-text "Nonstarter Study Register" output)))

(defun nonstarter-study-update (study-id &optional status results notes)
  "Update a study preregistration in the local Nonstarter DB."
  (interactive
   (list (read-string "Study ID: ")
         (read-string "Status (optional): ")
         (read-string "Results EDN (optional): ")
         (read-string "Notes (optional): ")))
  (let* ((args (list "--db" (nonstarter--db) "update" "--id" study-id))
         (args (if (nonstarter--blank-p status) args (append args (list "--status" status))))
         (args (if (nonstarter--blank-p results) args (append args (list "--results" results))))
         (args (if (nonstarter--blank-p notes) args (append args (list "--notes" notes))))
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
         (output (apply #'nonstarter--run-script
                        "scripts/nonstarter_study.clj"
                        "scripts.nonstarter-study"
                        args)))
    (nonstarter--show-text "Nonstarter Studies" output)))

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

(defun nonstarter--show (title payload)
  (let ((buf (get-buffer-create "*Nonstarter*")))
    (with-current-buffer buf
      (read-only-mode -1)
      (erase-buffer)
      (insert title "\n\n")
      (pp payload (current-buffer))
      (goto-char (point-min))
      (read-only-mode 1))
    (display-buffer buf)))

(defun nonstarter--format-kv (label value)
  (format "%s: %s" label value))

(defun nonstarter--format-map (label alist)
  (concat label ": "
          (if (and alist (listp alist))
              (mapconcat (lambda (pair)
                           (format "%s=%s" (car pair) (cdr pair)))
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

(defvar nonstarter-dashboard-mode-map
  (let ((map (make-sparse-keymap)))
    (define-key map (kbd "g") #'nonstarter-dashboard)
    (define-key map (kbd "b") #'nonstarter-personal-bid)
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
    map)
  "Keymap for nonstarter dashboard.")

(define-derived-mode nonstarter-dashboard-mode special-mode "Nonstarter"
  "Dashboard for Nonstarter.")

(defun nonstarter-dashboard ()
  "Show a dashboard for the current personal week."
  (interactive)
  (let* ((status (nonstarter--request "GET" "/api/personal/status"))
         (week (nonstarter--request "GET" "/api/personal/week"))
         (epoch (nonstarter--request "GET" "/api/personal/epoch"))
         (history (nonstarter--request "GET" "/api/personal/history?n=6"))
         (buf (get-buffer-create "*Nonstarter Dashboard*")))
    (with-current-buffer buf
      (read-only-mode -1)
      (erase-buffer)
      (insert "Nonstarter Dashboard\n\n")
      (insert (nonstarter--format-kv "Base URL" nonstarter-base-url) "\n")
      (insert (nonstarter--format-kv "Week" (alist-get 'week-id status)) "\n")
      (insert (nonstarter--format-kv "Bid total" (alist-get 'bid-total status)) "\n")
      (insert (nonstarter--format-kv "Clear total" (alist-get 'clear-total status)) "\n")
      (insert (nonstarter--format-kv "Unallocated" (alist-get 'unallocated status)) "\n\n")
      (insert "Epoch\n")
      (if (and epoch (listp epoch) (alist-get 'start-week epoch))
          (progn
            (insert (nonstarter--format-kv "Name" (alist-get 'name epoch)) "\n")
            (insert (nonstarter--format-kv "Start week" (alist-get 'start-week epoch)) "\n")
            (insert (nonstarter--format-kv "Weeks" (alist-get 'weeks epoch)) "\n")
            (insert (nonstarter--format-kv "Week index" (alist-get 'week-index epoch)) "\n")
            (insert (nonstarter--format-map "Epoch bids" (alist-get 'bids epoch)) "\n")
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
      (insert "Week bids\n")
      (insert (nonstarter--format-map "Bids" (alist-get 'bids week)) "\n")
      (insert (nonstarter--format-map "Clears" (alist-get 'clears week)) "\n\n")
      (insert "Categories\n")
      (if nonstarter-category-descriptions
          (dolist (entry nonstarter-category-descriptions)
            (insert (format "- %s: %s\n" (car entry) (cdr entry))))
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
      (goto-char (point-min))
      (nonstarter-dashboard-mode))
    (display-buffer buf)))

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
