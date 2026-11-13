import Foundation
import UIKit
import AVFoundation

@objc(PlaybackAwake)
final class PlaybackAwakeModule: NSObject {
  private static let allowedOwners: Set<String> = ["local-playback", "pip", "cast"]
  private let setIdleTimerDisabled: (Bool) -> Void
  private var owners = Set<String>()
  private var invalidated = false
  private var myoIntroPlayer: AVAudioPlayer?

  override init() {
    setIdleTimerDisabled = { UIApplication.shared.isIdleTimerDisabled = $0 }
    super.init()
  }

  init(setIdleTimerDisabled: @escaping (Bool) -> Void) {
    self.setIdleTimerDisabled = setIdleTimerDisabled
    super.init()
  }

  @objc static func requiresMainQueueSetup() -> Bool { true }

  @objc func setLocalPlaybackAwake(_ active: Bool) {
    setOwner("local-playback", active: active)
  }

  @objc func setPlaybackAwakeOwner(_ owner: String, active: Bool) {
    guard
      Self.allowedOwners.contains(owner),
      owner.range(of: #"^[a-z0-9-]{1,32}$"#, options: .regularExpression) != nil
    else {
      return
    }
    setOwner(owner, active: active)
  }

  // Fixed MYO Premium soundtrack supplied by the Android TV-approved
  // voice 6 master. Same recording on every supported iPhone.
  @objc func playMyoIntro() {
    let play = {
      guard !self.invalidated,
        let url = Bundle.main.url(forResource: "myo_intro_voice6", withExtension: "mp3")
      else { return }
      do {
        self.myoIntroPlayer?.stop()
        let player = try AVAudioPlayer(contentsOf: url)
        player.volume = 0.75
        player.prepareToPlay()
        self.myoIntroPlayer = player
        player.play()
      } catch {
        NSLog("[MYO] Intro audio unavailable: %@", String(describing: error))
      }
    }
    if Thread.isMainThread { play() }
    else { DispatchQueue.main.async(execute: play) }
  }

  @objc func stopMyoIntro() {
    let stop = {
      self.myoIntroPlayer?.stop()
      self.myoIntroPlayer = nil
    }
    if Thread.isMainThread { stop() }
    else { DispatchQueue.main.async(execute: stop) }
  }

  @objc func invalidate() {
    guard Thread.isMainThread else {
      DispatchQueue.main.async {
        self.invalidate()
      }
      return
    }
    guard !invalidated else { return }
    invalidated = true
    owners.removeAll()
    myoIntroPlayer?.stop()
    myoIntroPlayer = nil
    setIdleTimerDisabled(false)
  }

  private func setOwner(_ owner: String, active: Bool) {
    guard Thread.isMainThread else {
      DispatchQueue.main.async { [weak self] in
        self?.setOwner(owner, active: active)
      }
      return
    }
    guard !invalidated else { return }

    let changed: Bool
    if active {
      changed = owners.insert(owner).inserted
    } else {
      changed = owners.remove(owner) != nil
    }
    guard changed else { return }
    setIdleTimerDisabled(!owners.isEmpty)
  }
}
