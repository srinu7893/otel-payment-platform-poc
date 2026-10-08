import copy
import json
from pathlib import Path
import unittest
from run_release import normalize_traffic, preserved_traffic, validate_manifest_traffic, allocation


class TrafficContractTest(unittest.TestCase):
    def test_customer_before_after_example_is_actual_normalizer_output(self):
        example=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/customer-traffic.example.json').read_text())
        targets=preserved_traffic({'status':example['before']})
        self.assertEqual(targets,example['after']['traffic'])
        self.assert_exclusive(targets)

    def manifest(self, targets):
        return {'spec': {'traffic': targets, 'template': {'metadata': {'name': 'customer-candidate'}}}}

    def assert_exclusive(self, targets):
        for target in targets:
            self.assertFalse('revisionName' in target and target.get('latestRevision') is True)
            self.assertNotIn('latest_revision', target)
            self.assertNotIn('revision_name', target)

    def test_explicit_revision_removes_even_false_latest_flag(self):
        for flag in [True, False]:
            targets=normalize_traffic([{'revisionName':'customer-old','latestRevision':flag,'percent':100}])
            self.assertEqual(targets,[{'revisionName':'customer-old','percent':100}])
            self.assert_exclusive(targets)

    def test_latest_target_and_empty_revision(self):
        targets=normalize_traffic([{'revisionName':'','latestRevision':True,'percent':100}])
        self.assertEqual(targets,[{'latestRevision':True,'percent':100}])
        self.assert_exclusive(targets)
        validate_manifest_traffic(self.manifest(targets))

    def test_snake_case_and_mixed_input_prefer_explicit_revision(self):
        targets=normalize_traffic([{'revision_name':'old-a','latest_revision':True,'percent':70},
                                   {'revisionName':'old-b','latest_revision':True,'percent':30}])
        self.assert_exclusive(targets)
        self.assertEqual(allocation({'status':{'traffic':targets}}),{'old-a':70,'old-b':30})

    def test_guard_rejects_conflicting_raw_manifest_locally(self):
        for flag in ['latestRevision','latest_revision']:
            with self.assertRaisesRegex(ValueError,'mutually exclusive'):
                validate_manifest_traffic(self.manifest([{'revisionName':'old',flag:True,'percent':100}]))

    def test_split_and_tag_are_preserved_without_status_url(self):
        live={'status':{'traffic':[{'revisionName':'old-a','latestRevision':True,'percent':70},
                                  {'revisionName':'old-b','percent':30},
                                  {'revisionName':'old-tag','tag':'preview','url':'https://tag.run.app'}]}}
        original=copy.deepcopy(live)
        targets=preserved_traffic(live)
        self.assert_exclusive(targets)
        self.assertEqual(targets[-1],{'revisionName':'old-tag','tag':'preview'})
        self.assertEqual(live,original)
        validate_manifest_traffic(self.manifest(targets),{'old-a':70,'old-b':30})

    def test_latest_only_is_pinned_before_candidate_replacement(self):
        for key in ['latestReadyRevisionName','latest_ready_revision_name']:
            targets=preserved_traffic({'status':{key:'customer-old','traffic':[{'latest_revision':True,'percent':100}]}})
            self.assertEqual(targets,[{'revisionName':'customer-old','percent':100}])
            validate_manifest_traffic(self.manifest(targets),{'customer-old':100})
        with self.assertRaisesRegex(ValueError,'latestReadyRevisionName is missing'):
            preserved_traffic({'status':{'traffic':[{'latestRevision':True,'percent':100}]}})

    def test_candidate_cannot_receive_production_traffic_on_replace(self):
        for targets in [[{'latestRevision':True,'percent':100}],
                        [{'revisionName':'customer-candidate','percent':100}],
                        [{'revisionName':'old','percent':90},{'revisionName':'other','percent':10}]]:
            with self.assertRaises(ValueError):
                validate_manifest_traffic(self.manifest(targets),{'old':100})

    def test_missing_target_is_rejected(self):
        with self.assertRaises(ValueError):normalize_traffic([{'percent':100}])
