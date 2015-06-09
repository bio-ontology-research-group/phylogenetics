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
      }
    }
  }
}

matrix.each { taxon, c2s ->
  matrix.each { taxon2, c2s2 ->
    if (c2s.keySet().intersect(c2s2.keySet()).size()>0) { // taxon and taxon2 share characters
      c2s.each { character, state ->
	if (c2s2[character] == state) {
	  // do nothing
	} else {
	  // print difference
	  def s1 = states[state]
	  def s2 = states[c2s2[character]]
	  println ""+taxons[taxon].label+"\t"+taxons[taxon2].label+"\t"+characternames[character].label+"\t"+s1.phenotypes+"\t"+s2.phenotypes+"\t"+s1.label+"\t"+s2.label
	}
      }
    }
  }
}