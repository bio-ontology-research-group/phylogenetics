public class EntityQuality {
  def e1 = null
  def e2 = null
  def q = null
  
  public EntityQuality(e1, q) {
    this.e1 = e1
    this.q = q
  }
  public EntityQuality(e1, q, e2) {
    this.e1 = e1
    this.q = q
    this.e2 = e2
  }
}