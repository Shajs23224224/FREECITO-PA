#!/usr/bin/env python3
"""Exercise actual SQL literals on desktop SQLite; Android helper lifecycle is not tested."""
from pathlib import Path
import sqlite3,re
base=Path(__file__).resolve().parents[1]/'app/src/main/java/com/omega/ai/persistence'
schema=dict(re.findall(r'const val (\w+) = "([^"\n]+)"',(base/'PersistenceSchema.kt').read_text()))
for table,cls,alter,index in [('recall','CognitiveStore','RECALL_VERSION','RECALL_GLOBAL_INDEX'),('sessions','SessionStore','SESSION_VERSION','SESSION_RECENT_INDEX')]:
    code=(base/(cls+'.kt')).read_text()
    create=re.search(r'db.execSQL\("(CREATE TABLE [^"\n]+)"\)',code)[1]
    for migrate in [True,False]:
        with sqlite3.connect(':memory:') as db:
            db.execute(create.replace(', record_schema INTEGER NOT NULL DEFAULT 2','') if migrate else create)
            if table=='recall':
                db.execute('INSERT INTO recall(scope,kind,record_key,value,confidence,at_ms,source) VALUES(?,?,?,?,?,?,?)',('scope','SEMANTIC','key','value',.7,10,'synthetic'))
            else: db.execute('INSERT INTO sessions(id,started_ms) VALUES(?,?)',('id',10))
            if migrate: db.execute(schema[alter])
            db.execute(schema[index]);assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
            assert db.execute(f'SELECT record_schema FROM {table}').fetchone()==((1 if migrate else 2),)
            if table=='recall':
                query=re.search(r'rawQuery\("(SELECT record_key,value,confidence,at_ms,source[^"\n]+)"',code)[1]
                assert db.execute(query,('scope','SEMANTIC','32')).fetchone()==('key','value',.7,10,'synthetic')
                for key,value,confidence,ts,version in [('future','x',.7,11,99),('bad','',.7,12,2),('negative','x',.7,-1,2),('blob',b'x',.7,13,2)]:
                    db.execute('INSERT INTO recall VALUES(?,?,?,?,?,?,?,?)',('scope','SEMANTIC',key,value,confidence,ts,'test',version))
                assert len(db.execute(query,('scope','SEMANTIC','32')).fetchall())==1
                assert db.execute('SELECT count(*) FROM recall').fetchone()==(5,)
                assert db.execute(query,('other','SEMANTIC','32')).fetchall()==[]
            else: assert db.execute('SELECT id,started_ms,accepted FROM sessions').fetchone()==('id',10,0)
print('PASS: actual migration SQL v1->v2 and fresh v2, existing rows preserved, indexes, integrity, incompatible recalls excluded, scope isolation (desktop SQLite)')
