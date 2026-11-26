#include "engine.hpp"
#include <cstdlib>
#include <iostream>
#include <chrono>
#include <thread>
#include <vector>
#define REQUIRE(x) do { if(!(x)){std::cerr<<"FAIL line "<<__LINE__<<": "<<#x<<'\n'; std::exit(1);} }while(0)
using namespace omega;
int main(){
 Engine e; std::vector<uint8_t> rgba(4*4*4,255);
 REQUIRE(e.stats().state==State::idle); REQUIRE(!e.observe());
 REQUIRE(e.start()); REQUIRE(!e.start()); REQUIRE(e.stats().state==State::ready);
 REQUIRE(!e.submit(rgba.data(),rgba.size(),4,4,16,4,1));
 REQUIRE(e.observe()); REQUIRE(!e.observe());
 REQUIRE(!e.submit(nullptr,64,4,4,16,4,1)); REQUIRE(!e.submit(rgba.data(),63,4,4,16,4,1));
 REQUIRE(!e.submit(rgba.data(),64,4,4,15,4,1)); REQUIRE(!e.submit(rgba.data(),64,4,4,16,3,1));
 REQUIRE(!e.submit(rgba.data(),64,4097,4,16,4,1));
 REQUIRE(e.submit(rgba.data(),64,4,4,16,4,2)); REQUIRE(!e.submit(rgba.data(),64,4,4,16,4,2));
 for(int i=0;i<200 && e.stats().processed==0;++i) std::this_thread::sleep_for(std::chrono::milliseconds(2));
 REQUIRE(e.stats().processed==1); REQUIRE(e.stats().last_checksum==16*255);
 std::vector<uint8_t> padded(76,0); REQUIRE(e.submit(padded.data(),padded.size(),4,4,20,4,3));
 for(int i=4;i<5000;++i) REQUIRE(e.submit(rgba.data(),64,4,4,16,4,i));
 e.stop(); auto s=e.stats(); REQUIRE(s.state==State::stopped); REQUIRE(s.processed+s.dropped==s.accepted); REQUIRE(s.rejected==7);
 REQUIRE(!e.submit(rgba.data(),64,4,4,16,4,5000)); e.stop();
 REQUIRE(e.start()); REQUIRE(e.stats().accepted==0); REQUIRE(e.observe());
 std::thread producer([&]{for(int i=1;i<1000;++i)e.submit(rgba.data(),64,4,4,16,4,i);});
 e.stop(); producer.join(); REQUIRE(e.stats().state==State::stopped); REQUIRE(e.stats().processed+e.stats().dropped==e.stats().accepted);
 std::cout<<"PASS: lifecycle, bounds, timestamp, RGBA, row padding, backpressure, restart, concurrent stop\n";
}
