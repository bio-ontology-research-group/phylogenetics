
def labelf = new File("/mnt/phylogenetics/labels.txt")
def treef = new File("/mnt/phylogenetics/tree.txt")

def lab2name = [:]
labelf.splitEachLine("\t") { line ->
  def id = new Integer(line[0])
  def name = line[1].replaceAll("[^A-Za-z0-9]","_")
  if (name.size()>49) {
    name = name.substring(0,49)
  }
  lab2name[id] = name
}

def tree = treef.getText()
lab2name.each { key, val ->
  tree = tree.replaceAll("\\("+key+":", "\\("+val+":")
  tree = tree.replaceAll(","+key+":", ","+val+":")
}

println tree