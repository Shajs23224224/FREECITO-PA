import json
from pathlib import Path
x=json.loads((Path(__file__).resolve().parents[1]/'config/architecture.json').read_text())
assert (x['main'],x['intermediate'],x['final'],x['agents'],x['hidden_layers'],x['hidden_width'])==(824,32,2,160,16,2048)
ids=[n for d in x['domains'] for n in range(d['first'],d['last']+1)]
assert len(x['domains'])==16 and ids==list(range(1,825))
assert all(d['count']==d['last']-d['first']+1 for d in x['domains']) and sum(d['agents'] for d in x['domains'])==160
assert x['implemented_networks']==858 and x['implemented_dense_independent_networks']==0 and x['trained_networks']==0 and x['implemented_agents']==160 and not x['separate_trainer']
n=x['neural']; assert n['unique_parameters']==2*16*2048*4+858*(16*4+2048)
assert n['output_bias_update_parameters']==858*2048 and n['default_active_modules']==8
a=x['multi_agent']; assert a['teams']*a['roles_per_team']==160 and a['registered_and_executable_roles']==160 and a['fully_specialized_agents']==0
w=x['world']; assert len(set(w['memory_kinds']))==8 and w['automatic_kinds']==8 and w['max_total']==4096
r=x['reasoning']; assert r['max_plans']==16 and r['max_steps']==8 and not r['training']
e=x['executive']; assert e['max_in_flight']==1 and not e['real_money'] and not e['actual_game_execution']
l=x['learning']; assert l['head_parameters']==48 and l['eligible_parameters']==32 and l['actual_game_experiences']==0 and not l['training_separate']
print('PASS: architecture manifest, counts, non-overlapping domain ranges, no false implementation claims')
