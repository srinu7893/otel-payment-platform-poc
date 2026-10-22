import unittest
from unittest.mock import patch
from extended_observability import sandbox_login
from cloud_acceptance import request

class ExtendedColdStartTests(unittest.TestCase):
 def test_registration_and_login_allow_cold_chain_deadline(self):
  user={'username':'test-user','password':'secret'}
  with patch('extended_observability.request',side_effect=[{}, {'accessToken':'memory-only'}]) as call:
   self.assertEqual(sandbox_login('https://frontend.run.app',user),{'accessToken':'memory-only'})
   self.assertEqual(call.call_count,2)
   self.assertTrue(all(c.kwargs['timeout']==190 for c in call.call_args_list))
 def test_uncertain_registration_is_never_blindly_replayed(self):
  with patch('extended_observability.request',side_effect=TimeoutError) as call:
   with self.assertRaises(TimeoutError):sandbox_login('https://frontend.run.app',{'username':'test','password':'secret'})
   self.assertEqual(call.call_count,1)
 def test_request_timeout_is_bounded(self):
  with patch('cloud_acceptance.urllib.request.urlopen') as call:
   for timeout in (0,-1,241):
    with self.assertRaises(ValueError):request('https://frontend.run.app',timeout=timeout)
   call.assert_not_called()

if __name__=='__main__':unittest.main()
