(ns nonstarter.sidecar
  "Sidecar event helpers for nonstarter integration.")

(defn event-envelope
  "Wrap a sidecar event payload in a standard envelope.

   Required: :session-id, :event-type
   Optional: :turn, :source, :payload"
  [{:keys [session-id turn event-type source payload]}]
  {:event/id (str (random-uuid))
   :event/type event-type
   :session/id session-id
   :turn turn
   :source (or source {:system :nonstarter})
   :payload payload})

