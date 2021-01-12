// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

#ifndef NTCORE_CONNECTIONNOTIFIER_H_
#define NTCORE_CONNECTIONNOTIFIER_H_

#include "CallbackManager.h"
#include "Handle.h"
#include "IConnectionNotifier.h"
#include "ntcore_cpp.h"

namespace nt {

namespace impl {

class ConnectionNotifierThread
    : public CallbackThread<ConnectionNotifierThread, ConnectionNotification> {
 public:
  explicit ConnectionNotifierThread(int inst) : m_inst(inst) {}

  bool Matches(const ListenerData& /*listener*/,
               const ConnectionNotification& /*data*/) {
    return true;
  }

  void SetListener(ConnectionNotification* data, unsigned int listener_uid) {
    data->listener =
        Handle(m_inst, listener_uid, Handle::kConnectionListener).handle();
  }

  void DoCallback(
      std::function<void(const ConnectionNotification& event)> callback,
      const ConnectionNotification& data) {
    callback(data);
  }

  int m_inst;
};

}  // namespace impl

class ConnectionNotifier
    : public IConnectionNotifier,
      public CallbackManager<ConnectionNotifier,
                             impl::ConnectionNotifierThread> {
  friend class ConnectionNotifierTest;
  friend class CallbackManager<ConnectionNotifier,
                               impl::ConnectionNotifierThread>;

 public:
  explicit ConnectionNotifier(int inst);

  void Start();

  unsigned int Add(std::function<void(const ConnectionNotification& event)>
                       callback) override;
  unsigned int AddPolled(unsigned int poller_uid) override;

  void NotifyConnection(bool connected, const ConnectionInfo& conn_info,
                        unsigned int only_listener = UINT_MAX) override;

 private:
  int m_inst;
};

}  // namespace nt

#endif  // NTCORE_CONNECTIONNOTIFIER_H_
