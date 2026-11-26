#include "engine.hpp"
#include <algorithm>
namespace omega {
Engine::~Engine(){ stop(); }
bool Engine::start(){
  std::lock_guard<std::mutex> life(lifecycle_);
  std::lock_guard<std::mutex> lock(mutex_);
  if(state_==State::ready || state_==State::observing || worker_.joinable()) return false;
  accepted_=processed_=dropped_=rejected_=timestamp_=checksum_=0;
  pending_=false; frame_.clear(); state_=State::ready;
  try { worker_=std::thread(&Engine::loop,this); } catch (...) { state_=State::stopped; return false; }
  return true;
}
bool Engine::observe(){
  std::lock_guard<std::mutex> lock(mutex_);
  if(state_!=State::ready) return false;
  state_=State::observing; return true;
}
bool Engine::submit(const uint8_t* data,size_t bytes,int w,int h,int row,int pixel,int64_t ts){
  std::lock_guard<std::mutex> lock(mutex_);
  auto fail=[&]{ ++rejected_; return false; };
  if(state_!=State::observing || !data || w<1 || h<1 || w>4096 || h>4096 || pixel!=4 || row<0 || ts<=timestamp_) return fail();
  size_t minrow=static_cast<size_t>(w)*4;
  if(static_cast<size_t>(row)<minrow) return fail();
  size_t need=static_cast<size_t>(h-1)*static_cast<size_t>(row)+minrow;
  if(need>bytes) return fail();
  // Latest-frame queue of one, downsampled luminance. Never writes pixels to disk.
  const int step=std::max(1,(std::max(w,h)+319)/320);
  std::vector<uint8_t> sample;
  try { sample.reserve(static_cast<size_t>((w+step-1)/step)*((h+step-1)/step));
    for(int y=0;y<h;y+=step) for(int x=0;x<w;x+=step){
      const auto* p=data+static_cast<size_t>(y)*row+static_cast<size_t>(x)*4;
      sample.push_back(static_cast<uint8_t>((77u*p[0]+150u*p[1]+29u*p[2])>>8));
    }
  } catch (...) { return fail(); }
  if(pending_) ++dropped_;
  frame_.swap(sample); pending_=true; timestamp_=ts; ++accepted_; cv_.notify_one(); return true;
}
void Engine::loop(){
  for(;;){
    std::vector<uint8_t> local;
    { std::unique_lock<std::mutex> lock(mutex_); cv_.wait(lock,[&]{return pending_ || state_==State::stopped;});
      if(state_==State::stopped) return;
      local.swap(frame_); pending_=false;
    }
    // Real pixel processing smoke path, not object recognition or inference.
    uint64_t sum=0; for(auto v:local) sum+=v;
    { std::lock_guard<std::mutex> lock(mutex_); ++processed_; checksum_=static_cast<int64_t>(sum); }
  }
}
void Engine::stop(){
  std::lock_guard<std::mutex> life(lifecycle_);
  { std::lock_guard<std::mutex> lock(mutex_); state_=State::stopped;
    if(pending_) ++dropped_;
    pending_=false; frame_.clear(); cv_.notify_all(); }
  if(worker_.joinable()) worker_.join();
}
Stats Engine::stats() const { std::lock_guard<std::mutex> lock(mutex_); return {state_,accepted_,processed_,dropped_,rejected_,timestamp_,checksum_}; }
}
