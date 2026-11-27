import json
from pathlib import Path
x=json.loads((Path(__file__).resolve().parents[1]/'config/architecture.json').read_text())
assert (x['main'],x['intermediate'],x['final'],x['agents'],x['hidden_layers'],x['hidden_width'])==(824,32,2,160,16,2048)
ids=[n for d in x['domains'] for n in range(d['first'],d['last']+1)]
assert len(x['domains'])==16 and ids==list(range(1,825))
assert all(d['count']==d['last']-d['first']+1 for d in x['domains']) and sum(d['agents'] for d in x['domains'])==160
assert x['implemented_networks']==858 and x['implemented_dense_independent_networks']==0 and x['trained_networks']==0 and x['implemented_agents']==160 and not x['separate_trainer']
n=x['neural']; assert n['rank']==17 and n['residual_gates_per_module']==8 and n['unique_parameters']==2*16*2048*17+858*(16*17+2048+8)
assert n['output_bias_update_parameters']==858*2048 and n['default_active_modules']==8
a=x['multi_agent']; assert a['teams']*a['roles_per_team']==160 and a['registered_and_executable_roles']==160 and a['fully_specialized_agents']==0
w=x['world']; assert len(set(w['memory_kinds']))==8 and w['automatic_kinds']==8 and w['max_total']==4096
r=x['reasoning']; assert r['max_plans']==16 and r['max_steps']==8 and not r['training']
e=x['executive']; assert e['max_in_flight']==1 and not e['real_money'] and not e['actual_game_execution']
l=x['learning']; assert l['head_parameters']==88 and l['eligible_parameters']==72 and l['actual_game_experiences']==0 and not l['training_separate']
m=x['meta']; assert m['role_budgets']==[4,2,1,0] and m['resource_stop_latched'] and m['latency_window']==32
v=x['release']; assert x['version']=='1.0.12' and v['scope_and_freshness_gate'] and not v['gameplay_validated'] and not v['physical_device_validated']
a=x['accessibility']; assert a['configured'] and a['public_client_enabled'] and a['training_marker_required'] and a['max_pending']==1 and not a['delivery_is_reward']
assert not x['tactical']['live_adapter'] and x['tactical']['actual_game_experiences']==0
assert n['unique_parameters']+l['head_parameters']==3111624
assert n['independent_adapter_parameters']==1997424 and n['shared_weights']==1114112
assert not x['training_live']['manual_calibration'] and x['training_live']['automatic_actions']==['move','sprint','jump','crouch','prone','heal']
assert x['diagnostics']['persistent_trace'] and x['diagnostics']['max_entries']==100 and not x['diagnostics']['stores_screenshots']
assert x['vision']['yolo']['input_shape']==[1,3,416,416] and x['vision']['yolo']['output_shape']==[1,3549,85]
assert x['vision']['yolo']['class_count']==80 and not x['vision']['yolo']['game_specific_training']
print('PASS: architecture manifest, counts, non-overlapping domain ranges, no false implementation claims')
