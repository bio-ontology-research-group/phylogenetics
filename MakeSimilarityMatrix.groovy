def taxa = new TreeSet()
new File(args[0]).splitEachLine("\t") { line ->
  t = line[0]
  taxa.add(t)
}

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

def character2value2count = [:].withDefault { [:].withDefault { 0 } }
values.each { taxon, c2v ->
  characters.each { c ->
    if (c2v[c]!=null) {
      character2value2count[c][c2v[c]] += 1
    }
  }
}

def character2value2ic = [:].withDefault { [:].withDefault { 0.0 } }
character2value2count.each { c, v2c ->
  def total = 0
  v2c.values().each { total += it }
  v2c.each { v, count ->
    def p = count/total
    def ic = - Math.log(p)
    character2value2ic[c][v] = ic
  }
}

def flag = true
def lastTaxon = null
values.each { taxon, c2v ->
  if (flag) {
    values.keySet().each { print "\t$it"; lastTaxon = it }
    println ""
    flag = false
  }
  print "$taxon\t"
  values.each { taxon2, c2v2 ->
    def max = 0
    def match = 0
    c2v.each { character, value ->
      if (c2v2[character]!=null && c2v[character]!=null) {
	max += Math.max(character2value2ic[character][c2v2[character]], character2value2ic[character][c2v[character]])
	if (c2v2[character] == c2v[character]) { // same value for character
	  match += Math.max(character2value2ic[character][c2v2[character]], character2value2ic[character][c2v[character]])
	} else {
	  // do nothing
	}
      }
    }
    def sim = "NA"
    if (max > 0) {
      sim = 1-match/max
    }
    if (taxon2 == lastTaxon) {
      println "$sim"
    } else {
      print "$sim\t"
    }
  }
}