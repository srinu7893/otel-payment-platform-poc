import unittest
from api_test_logs import collect_entries,clean

class LogCollectionTests(unittest.TestCase):
 def test_pages_and_empty_search_pages_are_followed(self):
  calls=[];responses=[{'entries':[{'id':1}],'nextPageToken':'a'},{'nextPageToken':'b'},{'entries':[{'id':2}]}]
  def fetch(body):calls.append(body);return responses.pop(0)
  query={'filter':'trace=x','pageSize':1000}
  result=collect_entries(fetch,query)
  self.assertEqual(result,{'entries':[{'id':1},{'id':2}],'limited':False,'pagesRead':3})
  self.assertEqual([c.get('pageToken') for c in calls],[None,'a','b'])
  self.assertTrue(all(c['filter']=='trace=x' and c['pageSize']==1000 for c in calls))
 def test_entry_and_page_caps_remain_explicit(self):
  result=collect_entries(lambda _: {'entries':[1,2,3]}, {},max_entries=2)
  self.assertEqual(result['entries'],[1,2]);self.assertTrue(result['limited'])
  result=collect_entries(lambda _: {'nextPageToken':'next'}, {},max_pages=1)
  self.assertTrue(result['limited']);self.assertEqual(result['pagesRead'],1)
 def test_repeated_page_token_fails(self):
  with self.assertRaisesRegex(ValueError,'repeated'):collect_entries(lambda _: {'nextPageToken':'same'}, {})
 def test_invalid_bounds_fail_before_network(self):
  for kwargs in ({'max_entries':0},{'max_entries':10001},{'max_pages':0},{'max_pages':21}):
   with self.assertRaises(ValueError):collect_entries(lambda _:self.fail('Network called'),{},**kwargs)
 def test_logs_redact_credentials_and_jwts(self):
  text=clean('password="secret-value" accessToken="token-value" Bearer eyJabc.def.ghi')
  for secret in ('secret-value','token-value','eyJabc.def.ghi'):self.assertNotIn(secret,text)
  self.assertLessEqual(len(clean('x'*3000)),2000)

if __name__=='__main__':unittest.main()
