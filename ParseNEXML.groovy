import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*


XmlSlurper slurper = new XmlSlurper(false, false)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
def taxons = [:]
def characters = [:] // a map of a set of states for a character
def states = [:] // a map of a set of states for a character
def characternames = [:] // id to label
def matrix = [:]
new File("phenoscape-data/Curation Files/completed-phenex-files/").eachFileRecurse { file ->
  if (file.toString().indexOf("xml")>-1) {
    def albert = slurper.parse(file)

    albert.otus.otu.each { otus ->
      Expando exp = new Expando()
      exp.id = otus.@id.toString()
      exp.label = otus.@label
      exp.about = otus.@about
      taxons[exp.id] = exp
    }

    albert.characters.format.states.each { character -> // a character here is a bag of states
      def id = character.@id // id for the bag of states
      characters[id] = []
      character.state.each { state ->
	Expando exp = new Expando() // one character state
	exp.id = state.@id.toString()
	exp.label = state.@label
	exp.phenotypes = []
	state.meta.childNodes().each { phenotype ->
	  if (phenotype.name().indexOf("phen:phenotype")>-1) {
	    phenotype.childNodes().each { pcharacter ->
	      Expando c = new Expando()
	      pcharacter.childNodes().each { pt ->
		if (pt.name().indexOf("bearer")>-1) {
		  c.entity = pt.children()[0].attributes()["about"].toString()
		}
		if (pt.name().indexOf("quality")>-1) {
		  c.quality = pt.children()[0].attributes()["about"].toString()
		  pt.childNodes().each { e2 -> 
		    if (e2.name().indexOf("related_entity")>-1) {
		      c.e2 = e2.children()[0].attributes()["about"].toString()
		      println c.e2
		    }
		  }
		}
	      }
	      exp.phenotypes << c
	    }
	  }
	}
	characters[id] << exp
	states[exp.id] = exp
      }
    }

    albert.characters.format.char.each { c ->
      Expando exp = new Expando()
      exp.id = c.@id.toString()
      exp.states = c.@states.toString()
      exp.label = c.@label.toString()
      characternames[exp.id] = exp
    }

    albert.characters.matrix.row.each { row ->
      Expando exp = new Expando()
      exp.id = row.@id.toString()
      exp.otu = row.@otu.toString()
      matrix[exp.otu] = [:] // character:state
      exp.taxon = row.@otu
      row.cell.each { cell ->
	def character = cell.@char.toString()
	def state = cell.@state.toString()
	matrix[exp.otu][character] = state
	println ""+taxons[exp.otu]+"\t"+characternames[character]+"\t"+states[state]
      }
    }

  }
}

OWLOntologyManager manager = OWLManager.createOWLOntologyManager()
  
OWLDataFactory fac = manager.getOWLDataFactory()
def factory = fac


def ontSet = new TreeSet()

new File("ontologies/").eachFile {
  if (it.isFile()) {
    ontSet.add(manager.loadOntologyFromOntologyDocument(it))
  }
}

OWLOntology ontology = manager.createOntology(IRI.create("http://phenomebrowser.net/phylo"), ontSet)

def id2class = [:] // maps an OBO-ID to an OWLClass
ontology.getClassesInSignature(true).each {
  def a = it.toString()
  a = a.substring(a.indexOf("obo/")+4,a.length()-1)
  a = a.replaceAll("_",":")
  if (id2class[a] == null) {
    id2class[a] = it
  }
}
def ont = ontology

OWLReasonerFactory reasonerFactory = null

ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)

OWLReasonerFactory f1 = new ElkReasonerFactory()
OWLReasoner reasoner = f1.createReasoner(ont,config)
def hasquality = factory.getOWLObjectProperty(IRI.create("http://phenomebrowser.net/phylo#has-quality"))

matrix.each { k, v ->
  println taxons[k].label.toString()
  characternames.keySet().each { cn ->
    if (v[cn]!=null && states[v[cn]] != null && states[v[cn]].label !=null) {
      states[v[cn]].phenotypes.each { exp ->
	def e = id2class[exp.entity.toString()]
	def q = id2class[exp.quality.toString()]
	if (e && q) {
	  def cl = factory.getOWLClass(IRI.create("http://phenomebrowser.net/phylo#"+k))
	  def ax = factory.getOWLSubClassOfAxiom(cl, factory.getOWLObjectIntersectionOf(e, factory.getOWLObjectSomeValuesFrom(hasquality, q)))
	  manager.addAxiom(ont, ax)
	}
      }
    }
  }
}

reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

def taxon2classes = [:]
matrix.each { k, v ->
  def cl = factory.getOWLClass(IRI.create("http://phenomebrowser.net/phylo#"+k))
  taxon2classes[k] = reasoner.getSuperClasses(cl, false).getFlattened()
}
