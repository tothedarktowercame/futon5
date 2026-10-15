import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name("semi_robust_diagnostics.py")
SPEC = importlib.util.spec_from_file_location("semi_robust_diagnostics", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SemiRobustDiagnosticsTest(unittest.TestCase):
    def test_average_precision_is_deterministic_on_ties(self):
        scores = {"b": 1.0, "a": 1.0, "c": 0.0}
        self.assertEqual(MODULE.average_precision(scores, {"a"}), 1.0)

    def test_scores_cover_all_diagnostics_and_baselines(self):
        commits = [
            {"files": ["src/a.clj", "src/b.clj"]},
            {"files": ["src/a.clj", "src/c.clj"]},
        ]
        scores = MODULE.diagnostic_scores(commits, {"src/a.clj", "src/b.clj", "src/c.clj"})
        self.assertEqual(set(scores), set(MODULE.PAIRS) | set(MODULE.PAIRS.values()))
        self.assertEqual(scores["categorical-coupling"]["src/a.clj"], 2.0)
        self.assertEqual(scores["degree-centrality"]["src/a.clj"], 2.0)
        self.assertEqual(scores["churn-sensitivity"]["src/a.clj"], 1.0)
        self.assertEqual(scores["structural-entropy"]["src/a.clj"], 1.0)

    def test_summary_reports_variance_agreement_and_change_disagreements(self):
        batches = []
        for effect in (0.1, -0.1, 0.0):
            batches.append({"comparisons": {
                diagnostic: {"effect": effect, "ranking-agreement": 0.5}
                for diagnostic in MODULE.PAIRS
            }})
        base = {name: {"x": 1.0} for name in set(MODULE.PAIRS) | set(MODULE.PAIRS.values())}
        changed = {name: dict(values) for name, values in base.items()}
        changed["structural-entropy"]["x"] = 2.0
        summary = MODULE.summarize(batches, [base, changed, changed])
        self.assertAlmostEqual(summary["structural-entropy"]["effect-variance"], 0.0066666667)
        self.assertEqual(summary["structural-entropy"]["mean-ranking-agreement"], 0.5)
        self.assertEqual(summary["structural-entropy"]["change-disagreements"],
                         [{"path": "x", "changed": "structural-entropy"}])


if __name__ == "__main__":
    unittest.main()
