@Grab(group='org.semanticweb.elk', module='elk-owlapi-standalone', version='0.4.2')

import java.util.logging.Logger
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary

PrintWriter fout = new PrintWriter(new FileWriter("output.txt"))

OWLOntologyManager manager = OWLManager.createOWLOntologyManager()

OWLDataFactory fac = manager.getOWLDataFactory()
OWLDataFactory factory = fac

println "Loading ontology file..."
OWLOntology ont = manager.loadOntologyFromOntologyDocument(new File("../phenomeblast/ontology/phene.owl"))
println "Ontology file loaded..."

OWLReasonerFactory reasonerFactory = null

ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)

println "Classifying ontology..."
OWLReasonerFactory f1 = new ElkReasonerFactory()
OWLReasoner reasoner = f1.createReasoner(ont)
println "Ontology classified..."

def r = { String s ->
  if (s == "part-of") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/PHENOMENET_part-of"))
  } else if (s == "has-part") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/PHENOMENET_has-part"))
  } else if (s == "inheres-in") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/PHENOMENET_inheres-in"))
  } else if (s == "has-quality") {
    factory.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/PHENOMENET_has-quality"))
  } else {
    factory.getOWLObjectProperty(IRI.create("http://phenomebrowser.net/#"+s))
  }
}

def c = { String s ->
  factory.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/"+s))
}

println "Processing file..."
new File(args[0]).splitEachLine("\t") { line ->
  if (!line[0].startsWith("Taxon 1")) {
    def taxon = line[0]+" "+line[1]
    def e1 = line[2]?.replaceAll(":","_").split(" ")[0]
    def e2 = line[3]?.replaceAll(":","_").split(" ")[0]
    def p = line[4]?.replaceAll(":","_")
    def cl = fac.getOWLObjectSomeValuesFrom(r("has-part"), fac.getOWLObjectIntersectionOf(c(e1),fac.getOWLObjectSomeValuesFrom(r("has-quality"),c(p))))
    println cl
    reasoner.getEquivalentClasses(cl).each { sup ->
      fout.println("$taxon\tEquivalent: "+sup)
    }
    reasoner.getSuperClasses(cl, true).each { sup ->
      fout.println("$taxon\tSuperclass: "+sup)
    }
  }
}
