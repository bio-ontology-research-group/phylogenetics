import groovy.xml.*

XmlNodePrinter nodePrinter = new XmlNodePrinter()
def patofile = new File("quality.obo")

def id2super = [:]
def id2name = [:]
def id = ""
def values = new TreeSet()
def attributes = new TreeSet()
def obsolete = new TreeSet()
patofile.eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("name:")) {
    def name = line.substring(5).trim()
    id2name[id] = name
  }
  if (line.startsWith("is_a:") && line.indexOf("!")>-1) {
    def sc = line.substring(5, line.indexOf("!")-1).trim()
    if (id2super[id] == null) {
      id2super[id] = new TreeSet()
    }
    id2super[id].add(sc)
  }
  if (line.indexOf("attribute_slim")>-1) {
    attributes.add(id)
  }
  if (line.indexOf("value_slim")>-1) {
    values.add(id)
  }
  if (line.indexOf("is_obsolete: true")>-1) {
    obsolete.add(id)
  }
}

def quality2trait = [:]

/* now get the trait from PATO and generate a class for the trait */
// do a BFS (maybe change)
def search = new LinkedList()
values.each { q ->
  def found = false
  if (id2super[q]) {
    search.addAll(id2super[q])
    while (!found && search.size()>0) {
      def nq = search.poll()
      if (!(nq in attributes)) { // superclass is not a trait but another value
	if (id2super[nq]) {
	  search.addAll(id2super[nq])
	}
      } else { // found the trait (nq)
	found = true
	quality2trait[q] = nq
      }
    }
  }
}

XmlSlurper slurper = new XmlSlurper(false, false)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
def taxons = [:]
def characters = [:] // a map of a set of states for a character
def states = [:] // a map of a set of states for a character
def characters2labels = [:]
def taxons2states = [:].withDefault{ new LinkedHashSet() }

new File("phenoscape-data/Curation Files/completed-phenex-files/").eachFileRecurse { file ->
  if (file.toString().indexOf("xml")>-1) {
    def albert = slurper.parse(file)
    albert.otus.otu.each { otus ->
      Expando exp = new Expando()
      exp.id = otus.@id.toString()
      exp.label = otus.@label.toString()
      exp.about = otus.@about.toString()
      exp.treatment = file.toString()
      taxons[exp.id] = exp
    }
    albert.characters.format."char".each { ch ->
      def label = ch.@label
      def cid = ch.@id.toString()
      characters2labels[cid] = label
    }
    albert.characters.matrix.row.each { row ->
      def otu = row.@otu.toString()
      def taxonid = taxons[otu].id
      row.cell.each { cell ->
	Expando ex = new Expando()
	def character = cell.@char.toString()
	def state = cell.@state.toString()
	ex.character = characters2labels[character]
	ex.state = state
	taxons2states[taxonid].add(ex)
      }
    }
    albert.characters.format.states.state.each { state -> 
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
		String entityName = ""
		List l = new LinkedList()
		l.addAll ( pt.children() )
		while (l.size()>0) {
		  def ch = l.poll()
		  l.addAll( ch.children() )
		  if (ch.attributes()["about"]!=null) {
		    entityName += ch.attributes()["about"].toString()+" "
		  } else if (ch.attributes()["relation"]!=null) {
		    entityName += ch.attributes()["relation"].toString()+" "
		  }
		}
		c.entity = entityName
	      }
	      if (pt.name().indexOf("quality")>-1) {
		c.quality = pt.children()[0].attributes()["about"].toString()
		pt.childNodes().each { e2 -> 
		  if (e2.name().indexOf("related_entity")>-1) {
		    String entityName = ""
		    List l = new LinkedList()
		    l.addAll ( e2.children() )
		    while (l.size()>0) {
		      def ch = l.poll()
		      l.addAll( ch.children() )
		      if (ch.attributes()["about"]!=null) {
			entityName += ch.attributes()["about"].toString()+" "
		      } else if (ch.attributes()["relation"]!=null) {
			entityName += ch.attributes()["relation"].toString()+" "
		      }
		    }
		    c.e2 = entityName
		    //c.e2 = e2.children()[0].attributes()["about"].toString()
		    //println c.e2
		  }
		}
	      }
	    }
	    exp.phenotypes << c
	  }
	}
      	states[exp.id] = exp
      }
    }
  }
}
taxons2states.each { taxonid, cstates ->
  def taxon = taxons[taxonid]
  //  def taxonLabel = taxon.label + " " + taxon.treatment
  cstates.each { state ->
    def s = states[state.state]
    s.each { ss -> // ss is now one state, as represented in states; has id, phenotypes (set), and label
      ss.phenotypes.each { pheno -> // has entity, quality, e2
	def trait = pheno.quality
	if (trait!=null && trait in values && quality2trait[trait]!=null) {
	  trait = quality2trait[trait]
	}
	println taxon.label+"\t"+taxon.treatment+"\t"+pheno.entity+"\t"+pheno.e2+"\t"+pheno.quality+"\t"+trait+"\t"+ss.label
      }
    }
  }
}
