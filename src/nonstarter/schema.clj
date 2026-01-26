(ns nonstarter.schema
  "SQLite schema for nonstarter persistence.

   Tables mirror the desire market model:
   - pool: the collective resource
   - donations: money flowing in
   - proposals: memes seeking funding
   - votes: weak arrows of desire
   - funding_events: market clearing moments (the facts established)"
  (:require [next.jdbc :as jdbc]))

(def schema-ddl
  ["-- Pool state (singleton row)
    CREATE TABLE IF NOT EXISTS pool (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      balance REAL NOT NULL DEFAULT 0,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Donations into the pool
    CREATE TABLE IF NOT EXISTS donations (
      id TEXT PRIMARY KEY,
      amount REAL NOT NULL,
      donor TEXT DEFAULT 'anonymous',
      note TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Proposals: memes seeking funding
    CREATE TABLE IF NOT EXISTS proposals (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      description TEXT,
      ask REAL NOT NULL,
      sigil TEXT,
      proposer TEXT DEFAULT 'anonymous',
      status TEXT DEFAULT 'proposed',
      vote_weight REAL DEFAULT 0,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      funded_at TEXT
    )"

   "-- Votes: weak arrows expressing desire
    CREATE TABLE IF NOT EXISTS votes (
      id TEXT PRIMARY KEY,
      proposal_id TEXT NOT NULL REFERENCES proposals(id),
      voter TEXT DEFAULT 'anonymous',
      weight REAL DEFAULT 1,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      decayed_weight REAL
    )"

   "-- Funding events: the facts established
    --   'On this date, this much was committed to this desire'
    CREATE TABLE IF NOT EXISTS funding_events (
      id TEXT PRIMARY KEY,
      proposal_id TEXT NOT NULL REFERENCES proposals(id),
      amount REAL NOT NULL,
      note TEXT,
      fact_established TEXT NOT NULL,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Mana events: internal currency ledger (per session/turn)
    CREATE TABLE IF NOT EXISTS mana_events (
      id TEXT PRIMARY KEY,
      session_id TEXT NOT NULL,
      turn INTEGER,
      delta REAL NOT NULL,
      reason TEXT,
      note TEXT,
      balance REAL,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Sidecar events: adjunct metadata from portal/compass
    CREATE TABLE IF NOT EXISTS sidecar_events (
      id TEXT PRIMARY KEY,
      session_id TEXT NOT NULL,
      turn INTEGER,
      event_type TEXT NOT NULL,
      payload TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Hypotheses: statements to be tested in Nonstarter/MMCA
    CREATE TABLE IF NOT EXISTS hypotheses (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      statement TEXT NOT NULL,
      context TEXT,
      status TEXT DEFAULT 'active',
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Study preregistrations: planned tests linked to hypotheses
    CREATE TABLE IF NOT EXISTS study_preregistrations (
      id TEXT PRIMARY KEY,
      hypothesis_id TEXT REFERENCES hypotheses(id),
      study_name TEXT NOT NULL,
      design TEXT,
      metrics TEXT,
      seeds TEXT,
      status TEXT DEFAULT 'preregistered',
      results TEXT,
      notes TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Xenotypes: counterfactual timelines
    --   Fake people buying nonexistent products
    --   (capitalism's logic, fully explicit)
    CREATE TABLE IF NOT EXISTS xenotypes (
      id TEXT PRIMARY KEY,
      simulation_id TEXT NOT NULL,
      timeline_name TEXT,
      divergence_point TEXT,
      proposal_id TEXT REFERENCES proposals(id),
      fake_voter TEXT DEFAULT 'simulacrum',
      fake_amount REAL,
      counterfactual_outcome TEXT,
      notes TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Simulations: containers for xenotype branches
    CREATE TABLE IF NOT EXISTS simulations (
      id TEXT PRIMARY KEY,
      name TEXT,
      description TEXT,
      base_timeline TEXT DEFAULT 'actual',
      parameters TEXT,  -- JSON blob of simulation params
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Personal weeks: bids/clears for time allocation
    CREATE TABLE IF NOT EXISTS personal_weeks (
      week_id TEXT PRIMARY KEY,
      bids TEXT,
      clears TEXT,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Personal blocks: reconciled week summaries
    CREATE TABLE IF NOT EXISTS personal_blocks (
      id TEXT PRIMARY KEY,
      week_id TEXT NOT NULL REFERENCES personal_weeks(week_id),
      summary TEXT NOT NULL,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP
    )"

   "-- Indexes for common queries
    CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals(status)"
   "CREATE INDEX IF NOT EXISTS idx_votes_proposal ON votes(proposal_id)"
   "CREATE INDEX IF NOT EXISTS idx_funding_proposal ON funding_events(proposal_id)"
   "CREATE INDEX IF NOT EXISTS idx_hypotheses_status ON hypotheses(status)"
   "CREATE INDEX IF NOT EXISTS idx_study_hypothesis ON study_preregistrations(hypothesis_id)"
   "CREATE INDEX IF NOT EXISTS idx_study_status ON study_preregistrations(status)"
   "CREATE INDEX IF NOT EXISTS idx_mana_events_session ON mana_events(session_id)"
   "CREATE INDEX IF NOT EXISTS idx_sidecar_events_session ON sidecar_events(session_id)"
   "CREATE INDEX IF NOT EXISTS idx_xenotypes_simulation ON xenotypes(simulation_id)"
   "CREATE INDEX IF NOT EXISTS idx_personal_blocks_week ON personal_blocks(week_id)"

   "-- Initialize pool if empty
    INSERT OR IGNORE INTO pool (id, balance) VALUES (1, 0)"])

(defn create-schema!
  "Initialize the database schema."
  [ds]
  (doseq [ddl schema-ddl]
    (jdbc/execute! ds [ddl])))

(defn db-spec
  "Create a datasource spec for SQLite."
  [path]
  {:dbtype "sqlite"
   :dbname path})

(defn connect!
  "Connect to database and ensure schema exists."
  [path]
  (let [ds (jdbc/get-datasource (db-spec path))]
    (create-schema! ds)
    ds))
