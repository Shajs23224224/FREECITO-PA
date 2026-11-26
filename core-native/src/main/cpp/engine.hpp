#pragma once
#include <cstdint>
#include <cstddef>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <vector>
namespace omega {
// Infrastructure only: no policy, model weights or game commands in phase 1.
enum class State : int64_t { idle=0, ready=1, observing=2, stopped=3 };
struct Stats { State state; int64_t accepted, processed, dropped, rejected, last_timestamp, last_checksum; };
class Engine {
 public:
  Engine() = default;
  ~Engine();
  Engine(const Engine&) = delete;
  Engine& operator=(const Engine&) = delete;
  bool start();
  bool observe();
  bool submit(const uint8_t* data, size_t bytes, int width, int height, int row_stride, int pixel_stride, int64_t ts);
  void stop();
  Stats stats() const;
 private:
  void loop();
  mutable std::mutex lifecycle_, mutex_;
  std::condition_variable cv_;
  std::thread worker_;
  State state_=State::idle;
  bool pending_=false;
  std::vector<uint8_t> frame_;
  int64_t accepted_=0, processed_=0, dropped_=0, rejected_=0, timestamp_=0, checksum_=0;
};
}
