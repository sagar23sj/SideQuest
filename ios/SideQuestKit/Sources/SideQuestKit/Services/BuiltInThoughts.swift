import Foundation

// MARK: - Built-in thought corpus (Req 12.3)
//
// The on-device set of motivational "thoughts of the day" — centered on
// accomplishment and well-being (Req 12.1) — that the loading experience draws
// from with no network connection (Req 12.3). The set contains at least 30
// entries, each 1...280 characters; ids are stable so a given local date always
// resolves to the same thought (Req 12.2). Editing this list changes which
// thought maps to a given date but never breaks the ≥30 / 1...280 invariants,
// which the property test (task 17.2) guards.

extension BuiltInThoughtProvider {

    /// The built-in set of ≥30 motivational thoughts shipped on-device (Req
    /// 12.3). Each entry's `text` is 1...280 characters (Req 12.1) and each `id`
    /// is unique and stable. This is the default corpus for
    /// ``BuiltInThoughtProvider``.
    public static let builtInThoughts: [Thought] = [
        Thought(id: 1, text: "Every small step you take today is progress worth celebrating."),
        Thought(id: 2, text: "You don't have to do it all at once. One thing, done well, is enough for now."),
        Thought(id: 3, text: "Progress is quiet. Trust the work you put in even when you can't see the results yet."),
        Thought(id: 4, text: "Be kind to yourself today. You're doing better than your inner critic says."),
        Thought(id: 5, text: "A finished task, however small, is a gift to your future self."),
        Thought(id: 6, text: "Rest is part of the work. You can pause without falling behind."),
        Thought(id: 7, text: "Focus on the next right action, not the whole mountain."),
        Thought(id: 8, text: "What you start today doesn't have to be perfect. It just has to begin."),
        Thought(id: 9, text: "Your effort matters more than your speed. Keep going at your own pace."),
        Thought(id: 10, text: "Celebrate the things you already crossed off. Momentum builds on itself."),
        Thought(id: 11, text: "You are allowed to be proud of how far you've come."),
        Thought(id: 12, text: "Small consistent actions outlast big bursts of motivation. Show up again today."),
        Thought(id: 13, text: "Breathe. The task in front of you is smaller than the worry around it."),
        Thought(id: 14, text: "Done is better than perfect. Let yourself move forward."),
        Thought(id: 15, text: "Today is a fresh page. You get to decide what one thing makes it count."),
        Thought(id: 16, text: "Your worth isn't measured by your to-do list. Take care of yourself first."),
        Thought(id: 17, text: "Courage is starting before you feel ready. You're more ready than you think."),
        Thought(id: 18, text: "Each task you complete is proof that you can. Stack that evidence up."),
        Thought(id: 19, text: "Slow progress is still progress. You haven't stopped, and that counts."),
        Thought(id: 20, text: "Give your best to today and let that be enough."),
        Thought(id: 21, text: "You've handled hard days before. You can handle this one too."),
        Thought(id: 22, text: "Make space for what matters most, and let the rest wait."),
        Thought(id: 23, text: "The fact that you showed up today is already a win."),
        Thought(id: 24, text: "Turn one intention into one action. That's how plans become real."),
        Thought(id: 25, text: "You're allowed to start small. Tiny beginnings still lead somewhere."),
        Thought(id: 26, text: "Let today be about progress, not pressure."),
        Thought(id: 27, text: "Your energy is precious. Spend it on what truly moves you forward."),
        Thought(id: 28, text: "One completed step changes everything that comes after it."),
        Thought(id: 29, text: "Be patient with yourself. Growth takes the time it takes."),
        Thought(id: 30, text: "You don't need to feel motivated to begin. Action creates the motivation."),
        Thought(id: 31, text: "Look how much you've already carried. You are stronger than you give yourself credit for."),
        Thought(id: 32, text: "Choose one thing, finish it, and let that feel like a victory."),
        Thought(id: 33, text: "A calm mind gets more done than a rushed one. Slow down to speed up."),
        Thought(id: 34, text: "Today's effort is tomorrow's relief. Your future self is grateful."),
        Thought(id: 35, text: "You are capable of more than you imagine on your hardest days."),
        Thought(id: 36, text: "Take the first step. The path becomes clearer once you're moving.")
    ]
}
