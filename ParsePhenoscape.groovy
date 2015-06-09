import java.util.logging.Logger
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.mindswap.pellet.KnowledgeBase
import org.mindswap.pellet.expressivity.*
import org.mindswap.pellet.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*


def phenofile = new File("phenoscape_annotations")
def outfile = new File("/mnt/phylogenetics/labels.txt")

taxon2label = [:]
character2label = [:]
state2label = [:]
publication2label = [:]
character2eq = [:]
phenoscape = new HashSet()
phenofile.splitEachLine("\\|") { line ->
  if (line[0].indexOf("taxon_uid")==-1) {
    Expando exp = new Expando()
    exp.taxon = line[0]
    taxon2label[exp.taxon] = line[1]
    exp.character = line[2]
    character2label[exp.character] = line[3]
    exp.state = line[4]
    state2label[exp.state] = line[5]
    exp.publication = line[6]
    publication2label[exp.publication] = line[7]
    def entity1 = line[8]
    def quality = line[10]
    def entity2 = line[12]
    EntityQuality eq = new EntityQuality(entity1, quality, entity2)
    character2eq[exp.character] = eq
    exp.eq = eq
    phenoscape.add(exp)
  }
}

OWLOntologyManager manager = OWLManager.createOWLOntologyManager()

fac = manager.getOWLDataFactory()
def factory = fac

def onturi = "http://phenomebrowser.net/fish/"

def towards = fac.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_towards"))
def hasquality = fac.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_has_quality"))
def partof = fac.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_part_of"))
def haspart = fac.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/OBO_REL_has_part"))

def ontSet = new TreeSet()

ontSet.add(manager.loadOntologyFromOntologyDocument(new File("teleost_anatomy.obo")))
ontSet.add(manager.loadOntologyFromOntologyDocument(new File("spatial.obo")))
ontSet.add(manager.loadOntologyFromOntologyDocument(new File("quality.obo")))

OWLOntology ontology = manager.createOntology(IRI.create(onturi), ontSet)
def ont = ontology

id2class = [:] // maps an OBO-ID to an OWLClass
ontology.getClassesInSignature(true).each {
  def a = it.toString()
  a = a.substring(a.indexOf("obo/")+4,a.length()-1)
  a = a.substring(a.indexOf('#')+1)
  a = a.replaceAll("_",":")
  a = a.replaceAll("tp://bio2rdf.org/","")
  if (id2class[a] == null) {
    id2class[a] = it
  }
}

/* rewrite anatomy ontology so that part-of statements work correctly */
ontology.getClassesInSignature(true).each { cl ->
  if (cl.toString().indexOf("TAO")>-1) {
    def ax = fac.getOWLEquivalentClassesAxiom(cl, fac.getOWLObjectSomeValuesFrom(partof, cl))
    manager.addAxiom(ont,ax)
  }
}

OWLReasonerFactory reasonerFactory = null

ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)

OWLReasonerFactory fac1 = new ElkReasonerFactory()
OWLReasoner reasoner = fac1.createReasoner(ont,config)

reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)


def parseEntityStatements(String s) {
  if (s==null) return null
  s = s.trim()
  if (s.indexOf("^") == -1) {
    return id2class[s]
  } else {
    def s1 = s.substring(0,s.indexOf("^"))
    def r = s.substring(s.indexOf("^")+1, s.indexOf("(")).replaceAll(":","_")
    def s2 = s.substring(s.indexOf("(")+1, s.length()-1)
    def s1c = parseEntityStatements(s1)
    def s2c = parseEntityStatements(s2)
    if (s1c!=null) {
      if (s2c!=null) {
	return fac.getOWLObjectIntersectionOf(s1c, fac.getOWLObjectSomeValuesFrom(fac.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/"+r)), s2c))
      } else {
	return s1c
      }
    } else { 
      return null 
    }
  }
}

def character2class = [:]
character2eq.each { key, value ->
  def c1 = parseEntityStatements(value.e1)
  def c2 = parseEntityStatements(value.e2)
  def q = parseEntityStatements(value.q)
  if (c1!=null && q!=null) {
    def cl = null
    if (c2!=null) {
      cl = fac.getOWLObjectIntersectionOf(c1, 
					  fac.getOWLObjectSomeValuesFrom(hasquality,
									 fac.getOWLObjectIntersectionOf(
									   q, fac.getOWLObjectSomeValuesFrom(towards, c2))))
    } else {
      cl = fac.getOWLObjectIntersectionOf(c1, fac.getOWLObjectSomeValuesFrom(hasquality, q))
    }
    character2class[key] = cl
    def characterclass = fac.getOWLClass(IRI.create(onturi+key))
    def ax = fac.getOWLEquivalentClassesAxiom(characterclass, cl)
    manager.addAxiom(ont,ax)
  }
}

reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

character2eq.each { key, value ->
  if (ont.containsClassInSignature(IRI.create(onturi+key))) {
    def characterclass = fac.getOWLClass(IRI.create(onturi+key))
    def s1 = new HashSet()
    def superc = reasoner.getSuperClasses(characterclass, false)?.getFlattened()
    def equivc = reasoner.getEquivalentClasses(characterclass)
    if (superc != null) {
      s1.addAll(superc)
    }
    if (equivc != null) {
      s1.addAll(equivc)
    }
    character2class[key] = s1
  }
}

def taxon2characterstate = [:]
phenoscape.each { p ->
  def taxon = p.taxon
  if (taxon2characterstate[taxon] == null) {
    taxon2characterstate[taxon] = [:]
  }
  def c = character2class[p.character]
  def s = p.state
  c.each { ccl ->
    if (taxon2characterstate[taxon][ccl]==null) {
      taxon2characterstate[taxon][ccl] = new TreeSet()
    }
    taxon2characterstate[taxon][ccl].add(s)
  }
}

Double taxonSimilarity(Map s1, Map s2) { // Map is: character -> {state1, ..., stateN}
  double total = 0
  double overlap = 0
  s1.keySet().each { c1 ->
    if (s1[c1].size()==1) { // use only those characters with a single state (for now)
      if (s2[c1]!=null && s2[c1].size()==1 ) {
	def cs1 = s1[c1].collect { state2label[it] }
	def cs2 = s2[c1].collect { state2label[it] }
	/*	def cs1 = s1[c1]
		def cs2 = s2[c1]*/
	if (cs1.intersect(cs2).size()==1) {
	  overlap+=1
	  total+=1
	} else {
	  total+=1
	}
      }
    }
  }
  if (total == 0) {
    0.0
  } else {
    overlap/total
  }
}

def taxon2number = [:]
def counter = 0
taxon2characterstate.keySet().each { t ->
  taxon2number[t] = counter
  counter+=1
}

def fout = new PrintWriter(new BufferedWriter(new FileWriter(outfile)))
taxon2number.each { key, value ->
  fout.println(value+"\t"+taxon2label[key])
}
fout.flush()
fout.close()
System.exit(0)

boolean first = true
taxonsimilarity = [:]
/* writes the distance matrix in Phylip format */
taxon2characterstate.each { taxon1, character1 ->
  if (first) { // write header
    first = false
    fout.println(taxon2characterstate.keySet().size())
  }
  if (taxonsimilarity[taxon1] == null) {
    taxonsimilarity[taxon1] = [:] // a map from taxon to similarity value
  }
  println taxon2number[taxon1]+"\t"+taxon2label[taxon1]
  def ts = taxon2number[taxon1].toString().padLeft(10)
  fout.print(ts+" ")
  taxon2characterstate.each { taxon2, character2 ->
    def sim = 1-taxonSimilarity(character1, character2)
    taxonsimilarity[taxon1][taxon2] = sim
    fout.print(sim+" ")
  }
  fout.println("")
}
fout.flush()
fout.close()
