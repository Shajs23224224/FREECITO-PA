package com.omega.core.agents

import com.omega.core.neural.NeuralEngine
import kotlin.math.abs
import java.util.ArrayDeque

data class Role(val id:String,val domain:Int,val name:String,val key:String,val modules:List<String>)
data class Evidence(val key:String,val value:Float,val confidence:Float,val source:String,val seenNs:Long)
data class Proposal(val agent:String,val key:String,val hypothesis:String,val alternative:String,val confidence:Float,val value:Float?,val modules:List<String>)
data class BoardResult(val frame:Long,val proposals:List<Proposal>,val selected:List<Proposal>,val activeModules:List<String>,val decision:FloatArray,val execution:FloatArray)

object AgentRegistry {
    private val names=listOf(
        "Analista de imágenes|Detector de objetos virtuales|Reconocedor de personajes|Clasificador de entidades|Analista de colores y formas|Especialista en segmentación|Especialista en seguimiento visual|Analista de movimiento|Supervisor de calidad perceptiva|Integrador multimodal",
        "Estimador de profundidad|Analista de perspectiva|Reconocedor de geometrías|Especialista en orientación espacial|Reconocedor de obstáculos|Analista de estructuras|Estimador de distancias|Constructor de mapas internos|Supervisor de incertidumbre espacial|Integrador de representaciones 3D",
        "Lector OCR|Intérprete de textos|Analista del HUD|Reconocedor de iconos|Intérprete de menús|Analista de notificaciones|Reconocedor de eventos|Especialista en objetivos|Clasificador de situaciones|Analista contextual",
        "Explorador|Planificador de rutas|Analista de accesibilidad|Evaluador de desplazamientos|Especialista en orientación|Supervisor de movimiento|Detector de bloqueos|Corrector de trayectorias|Reconocedor de lugares|Coordinador de navegación",
        "Analista de personajes adversarios|Analista de encuentros|Evaluador de acciones disponibles|Coordinador de habilidades virtuales|Analista del equipamiento|Supervisor del estado del personaje|Evaluador de enfrentamientos|Analista de resultados|Especialista en supervivencia dentro del juego|Coordinador de decisiones de combate",
        "Generador de alternativas|Evaluador de oportunidades|Analista de riesgos|Especialista en prioridades|Evaluador de escenarios|Analista de consecuencias|Especialista en cambios de contexto|Coordinador de decisiones inmediatas|Crítico táctico|Integrador táctico",
        "Planificador de objetivos|Estratega general|Especialista en objetivos secundarios|Analista de progresión|Planificador de largo plazo|Especialista en gestión temporal|Evaluador de resultados potenciales|Analista de probabilidades|Revisor estratégico|Coordinador estratégico",
        "Predictor de estados|Analista de tendencias|Predictor temporal|Evaluador de incertidumbre|Especialista en modelos causales|Analista de cambios ambientales|Predictor de resultados|Evaluador de hipótesis|Calibrador probabilístico|Coordinador predictivo",
        "Administrador de inventario|Analista de municiones virtuales|Gestor de objetos consumibles|Administrador de equipamiento|Analista de capacidades|Gestor de recursos escasos|Reconocedor de objetos útiles|Analista de necesidades|Evaluador de eficiencia|Coordinador de recursos",
        "Analista de moneda virtual|Reconocedor de tiendas|Evaluador de precios|Analista de utilidad|Administrador de presupuesto|Comparador de artículos|Analista de coste-beneficio|Gestor de ahorro virtual|Supervisor de transacciones|Coordinador económico",
        "Especialista en aprendizaje por refuerzo|Analista de recompensas|Supervisor de exploración|Supervisor de explotación del conocimiento|Administrador de experiencias|Corrector de errores|Evaluador de políticas|Supervisor de estabilidad neuronal|Especialista en aprendizaje incremental|Coordinador del aprendizaje",
        "Gestor de memoria inmediata|Gestor de memoria de trabajo|Gestor de memoria episódica|Gestor de memoria semántica|Gestor de memoria espacial|Gestor de memoria procedimental|Gestor de memoria estratégica|Gestor de memoria prospectiva|Evaluador de relevancia|Coordinador de consolidación",
        "Razonador deductivo|Razonador inductivo|Razonador abductivo|Analista causal|Razonador probabilístico|Evaluador contrafactual|Detector de contradicciones|Verificador de hipótesis|Especialista en resolución de problemas|Coordinador lógico",
        "Analista de situaciones desconocidas|Especialista en generalización|Supervisor de cambios|Adaptador de comportamientos|Evaluador de nuevas habilidades|Analista de patrones emergentes|Especialista en transferencia de conocimiento|Detector de degradación|Supervisor de plasticidad neuronal|Coordinador adaptativo",
        "Autoevaluador|Crítico de decisiones|Supervisor de confianza|Detector de errores cognitivos|Analista de rendimiento|Evaluador de consistencia|Supervisor de objetivos|Gestor de incertidumbre|Especialista en autocorrección|Coordinador metacognitivo",
        "Director cognitivo|Administrador neuronal|Enrutador de expertos|Supervisor de concurrencia|Gestor de prioridades|Árbitro de desacuerdos|Administrador computacional|Supervisor de acciones|Director de integración|Coordinador ejecutivo general"
    )
    val roles:List<Role> = names.flatMapIndexed { d,line ->
        val first=1+if(d<8)d*52 else 416+(d-8)*51
        val count=if(d<8)52 else 51
        line.split('|').mapIndexed { i,name ->
            val id="A"+(d*10+i+1).toString().padStart(3,'0')
            Role(id,d,name,"evidence.$id",(i until count step 10).map { "P"+(first+it).toString().padStart(3,'0') })
        }
    }
}

/** Bounded deliberation. Neural embeddings are experimental; never interpreted as confidence or actions. */
class AgentBoard(private val neural:NeuralEngine,private val ttlNs:Long=4_000_000_000L) {
    private var round=0L
    private val visits=mutableMapOf<String,Int>()
    private var last=-1L
    private val history=ArrayDeque<List<Proposal>>()
    @Synchronized fun recent():List<List<Proposal>> = history.map{it.toList()}
    @Synchronized fun deliberate(frame:Long,now:Long,input:FloatArray,evidence:List<Evidence>,domains:List<Int>,budget:Int=4):BoardResult {
        require(frame>last && frame>=0 && now>=0 && budget in 1..8 && domains.isNotEmpty() && domains.all{it in 0..15})
        require(input.size==2048 && input.all{it.isFinite() && abs(it)<=100f} && evidence.size<=320)
        val valid=evidence.filter { it.seenNs in 0..now && now-it.seenNs<=ttlNs && it.value.isFinite() && it.value in 0f..1f && it.confidence.isFinite() && it.confidence in 0f..1f && it.source.isNotBlank() }.groupBy{it.key}
        val eligible=AgentRegistry.roles.filter{it.domain in domains}
        val offset=(round%eligible.size).toInt()
        val chosen=(eligible.drop(offset)+eligible.take(offset)).sortedByDescending { if(valid.containsKey(it.key))1 else 0 }.take(budget)
        val proposals=mutableListOf<Proposal>();val active=linkedSetOf<String>();val fused=FloatArray(2048)
        for(role in chosen){
            val observations=valid[role.key].orEmpty().groupBy{it.source}.map{(_,v)->v.maxBy{it.seenNs}}
            val best=observations.maxByOrNull{it.confidence}
            val conflict=best!=null && observations.any{abs(it.value-best.value)>.25f && it.confidence>=.5f}
            val hypothesis=when { best==null->"missing"; conflict->"conflict"; best.confidence<.6f->"uncertain";else->"observed" }
            val confidence=if(hypothesis=="observed")best!!.confidence else 0f
            val visit=visits.getOrDefault(role.id,0); val main=role.modules[visit%role.modules.size]; visits[role.id]=(visit+1)%role.modules.size
            val m1="I"+(role.domain*2+1).toString().padStart(2,'0');val m2="I"+(role.domain*2+2).toString().padStart(2,'0')
            val features=input.copyOf();features[2000]=best?.value?:0f;features[2001]=confidence;features[2002]=if(conflict)1f else 0f;features[2003]=role.id.drop(1).toInt()/160f
            val p=neural.forward(main,features);val a=neural.forward(m1,p);val b=neural.forward(m2,p)
            active.addAll(listOf(main,m1,m2));for(i in fused.indices)fused[i]+=(a[i]+b[i])/(2*chosen.size)
            proposals+=Proposal(role.id,role.key,hypothesis,"request_observation",confidence,if(hypothesis=="observed")best?.value else null,listOf(main,m1,m2))
        }
        val selected=proposals.filter{it.hypothesis=="observed"}.sortedWith(compareByDescending<Proposal>{it.confidence}.thenBy{it.agent})
        val decision=neural.forward("F01",fused);val execution=neural.forward("F02",decision);active.addAll(listOf("F01","F02"))
        last=frame;round+=chosen.size;history.addLast(proposals.toList());if(history.size>32)history.removeFirst()
        return BoardResult(frame,proposals.toList(),selected,active.toList(),decision,execution)
    }
}
