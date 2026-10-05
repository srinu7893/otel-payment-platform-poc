import tempfile
import unittest
from pathlib import Path
from server import Inbox
class InboxTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.path=Path(self.tmp.name)/'inbox.db';self.inbox=Inbox(self.path)
 def tearDown(self):self.tmp.cleanup()
 def payload(self,status='firing',start='2026-10-05T00:00:00Z'):
  return {'alerts':[{'fingerprint':'synthetic','startsAt':start,'endsAt':'2026-10-05T01:00:00Z','status':status,'labels':{'alertname':'Test','owner':'poc-support'},'annotations':{'summary':'safe drill'}}]}
 def test_duplicate_ack_resolve_and_restart(self):
  self.inbox.ingest(self.payload());self.inbox.ingest(self.payload());self.assertEqual(len(self.inbox.list()),1)
  item=self.inbox.list()[0];self.assertTrue(self.inbox.acknowledge(item['id']));ack=self.inbox.list()[0]['acknowledged']
  self.inbox.acknowledge(item['id']);self.assertEqual(self.inbox.list()[0]['acknowledged'],ack)
  self.inbox.ingest(self.payload('resolved'));self.inbox.ingest(self.payload())
  persisted=Inbox(self.path).list()[0];self.assertEqual(persisted['status'],'resolved');self.assertEqual(persisted['acknowledged'],ack)
  self.inbox.ingest(self.payload(start='2026-10-06T00:00:00Z'));self.assertEqual(len(self.inbox.list()),2)
 def test_invalid_batch_does_not_partially_write(self):
  payload=self.payload();payload['alerts'].append({'status':'unknown'})
  with self.assertRaises(ValueError):self.inbox.ingest(payload)
  self.assertEqual(self.inbox.list(),[])
 def test_arbitrary_payload_and_credentials_are_not_retained(self):
  p=self.payload();p['alerts'][0]['annotations']['password']='synthetic-secret';p['token']='synthetic-secret'
  self.inbox.ingest(p);self.assertNotIn('synthetic-secret',str(self.inbox.list()))
  self.assertFalse(self.inbox.acknowledge('missing'))
if __name__=='__main__':unittest.main()
