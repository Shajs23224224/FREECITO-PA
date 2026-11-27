package com.omega.vision
fun main(){
    val cache=ExactFrameCache<List<String>>(8000)
    val pixels=intArrayOf(1,2,3,4)
    check(cache.lookup(pixels,2,2,1)==null)
    cache.store(pixels,2,2,10,listOf("ABC"))
    check(cache.lookup(pixels,2,2,11)==listOf("ABC"))
    check(cache.lookup(pixels,2,2,10)==null)
    check(cache.lookup(pixels,2,2,9)==null)
    check(cache.lookup(pixels,2,2,8010)==listOf("ABC"))
    check(cache.lookup(pixels,2,2,8011)==null)
    check(cache.lookup(pixels,4,1,11)==null)
    pixels[3]=5
    check(cache.lookup(pixels,2,2,11)==null)
    check(cache.lookup(intArrayOf(1,2,3,4),2,2,12)==listOf("ABC"))
    cache.store(pixels,2,2,20,emptyList())
    check(cache.lookup(pixels,2,2,21)==emptyList<String>())
    cache.clear()
    check(cache.lookup(pixels,2,2,22)==null)
    println("PASS: exact OCR cache identity, mutation, dimensions, monotonic input, bounded age, empty results and invalidation")
}
