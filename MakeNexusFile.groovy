// creates nexus file from TSV

def taxa = new TreeSet()
new File(args[0]).splitEachLine("\t") { line ->
  t = line[0]
  taxa.add(t)
}

println "#NEXUS"
println "BEGIN TAXA;\n\tTITLE taxa_block;\n\tDIMENSIONS  NTAX="+taxa.size()+";"
println "\tTAXLABELS"
print "\t\t"
taxa.each { tax ->
  tax = tax.replaceAll(" ","_")
  print "$tax "
}
println "\t;\nEND;\n\n"

def characters = new TreeSet()
def values = [:].withDefault { [:] } // taxon -> character -> value or taxon -> character -> null if no value
def cvalues = new TreeSet()
new File(args[0]).splitEachLine("\t") { line ->
  def taxon = line[0]
  def e1 = line[2]
  def e2 = line[3]
  def q = line[4]
  def traits = line[5]
  def label = line[6]
  if (q == traits) {
    q = label
  }
  def character = "$e1/$e2/$traits"
  characters.add(character)
  values[taxon][character] = q
  def cvalue = "$character $q"
  cvalues.add(cvalue)
}


def character2value = [:].withDefault { new TreeSet() }
values.each { taxon, c2v ->
  c2v.each { c, v ->
    if (v!=null) {
      character2value[c].add(v)
    }
  }
}

// Filter multi-character states
character2value = character2value.findAll { k, v -> v.size() <= 32 }

def character2value2symbol = [:].withDefault { [:] }
character2value.each { c, v -> 
  def count = 'A'
  v.each { val ->
    character2value2symbol[c][val] = count
    if (count!='Z') {
      count++
    } else {
      count = '1'
    }
  }
}

println "BEGIN CHARACTERS;"
println "\tTITLE  Untitled_Character_Matrix;"
println "\tDIMENSIONS  NCHAR="+characters.size()+";"
println "\tFORMAT DATATYPE=STANDARD MISSING=? RESPECTCASE;"
println "\tMATRIX"
values.each { taxon, c2v ->
  taxon = taxon.replaceAll(" ","_")
  print "\t$taxon    "
  characters.each { c ->
    if (c2v[c]!=null) {
      print character2value2symbol[c][c2v[c]]
    } else {
      print "?"
    }
  }
  println ""
}
println ";"
println "END;"
