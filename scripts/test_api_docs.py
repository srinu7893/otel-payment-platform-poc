import importlib.util,json,re,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('api_catalog',Path(__file__).with_name('build-api-docs.py'));catalog=importlib.util.module_from_spec(spec);spec.loader.exec_module(catalog)
class ApiDocsTest(unittest.TestCase):
 def test_every_controller_handler_is_in_inventory_and_internal_routes_stay_private(self):
  rows=catalog.inventory();services={r['service'] for r in rows}
  self.assertEqual(services,set(catalog.SERVICES)|{'frontend'})
  self.assertEqual(len({(r['service'],r['method'],r['path']) for r in rows}),len(rows))
  for row in rows:
   self.assertNotIn(':',row['path'])
   if row['service'] in ['gateway-service','mock-bank-service'] or row['path'].startswith('/internal/'):
    self.assertTrue(row['private'])
  public=catalog.spec('public',[r for r in rows if not r['private']])
  self.assertNotIn('/api/v1/bank/debits',public['paths']);self.assertNotIn('/api/v1/admin/accounts',public['paths'])
  for path in ['/api/v1/auth/register','/api/v1/wallet/deposits','/api/v1/money-requests/{id}/pay','/api/v1/money-requests/{id}/decline','/api/v1/money-requests/{id}/cancel','/api/v1/notifications/{id}']:
   self.assertIn(path,public['paths'])
 def test_required_body_fields_and_constraints_come_from_the_real_records(self):
  rows=catalog.inventory()
  for row in rows:
   operation,body=catalog.operation(row)
   if row['bodyType'] or row['path'].endswith('/rum'):
    contract=operation['requestBody']['content']['application/json'];self.assertIsInstance(body,dict)
    self.assertTrue(set(contract['schema'].get('required',[]))<=set(body),row['path'])
    for name,field in contract['schema'].get('properties',{}).items():
     if name in body and field.get('pattern'):self.assertTrue(re.fullmatch(field['pattern'],str(body[name])),(row['path'],name))
  deposit=next(r for r in rows if r['service']=='payment-service' and r['path']=='/api/v1/wallet/deposits')
  properties=catalog.operation(deposit)[0]['requestBody']['content']['application/json']['schema']['properties']
  self.assertEqual(properties['amount']['minimum'],.01);self.assertEqual(properties['amount']['maximum'],100000)
 def test_security_and_examples_are_usable_without_putting_tokens_in_specs(self):
  for row in catalog.inventory():
   op,_=catalog.operation(row)
   if row['private']:self.assertEqual(op['security'],[{'cloudRunIdentity':[]}])
   elif row['path'].startswith('/api/v1/auth/') or row['path'].endswith('/rum') or row['service']=='frontend':self.assertEqual(op['security'],[])
   else:self.assertEqual(op['security'],[{'bearerAuth':[]}])
  for path in (catalog.ROOT/'frontend/public/api-docs/specs').glob('*.json'):
   self.assertNotRegex(path.read_text(),r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.')
if __name__=='__main__':unittest.main()
