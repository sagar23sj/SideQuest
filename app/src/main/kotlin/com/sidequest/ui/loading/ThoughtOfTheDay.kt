package com.sidequest.ui.loading

import java.time.LocalDate

/**
 * A short, attributed motivational quote shown on the loading screen (Req 6d).
 */
data class Thought(val text: String, val author: String)

/**
 * Supplies the "thought of the day" for the loading experience (Req 6d).
 *
 * The set is built in (works fully offline, Req 6d.3) and the selection is
 * deterministic per calendar day — the same day always shows the same thought,
 * and it advances the next day (Req 6d.2). Quotes lean toward doing,
 * accomplishment, and well-being over passive consumption, matching the
 * SideQuest tone.
 */
object ThoughtOfTheDay {

    /** Returns the thought for [date] (defaults to today), chosen deterministically. */
    fun forDate(date: LocalDate = LocalDate.now()): Thought {
        // epochDay is a stable, monotonically increasing per-day index.
        val index = (date.toEpochDay() % QUOTES.size).toInt().let { if (it < 0) it + QUOTES.size else it }
        return QUOTES[index]
    }

    /**
     * A curated set of widely-attributed short quotes about action, doing, and
     * well-being. Kept to single lines so they render cleanly on the loader.
     */
    val QUOTES: List<Thought> = listOf(
        Thought("The secret of getting ahead is getting started.", "Mark Twain"),
        Thought("Well done is better than well said.", "Benjamin Franklin"),
        Thought("Action is the foundational key to all success.", "Pablo Picasso"),
        Thought("The way to get started is to quit talking and begin doing.", "Walt Disney"),
        Thought("It always seems impossible until it's done.", "Nelson Mandela"),
        Thought("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
        Thought("A year from now you may wish you had started today.", "Karen Lamb"),
        Thought("Start where you are. Use what you have. Do what you can.", "Arthur Ashe"),
        Thought("The journey of a thousand miles begins with one step.", "Lao Tzu"),
        Thought("You don't have to be great to start, but you have to start to be great.", "Zig Ziglar"),
        Thought("Little by little, one travels far.", "J.R.R. Tolkien"),
        Thought("Quality is not an act, it is a habit.", "Aristotle"),
        Thought("Happiness is not something ready made. It comes from your own actions.", "Dalai Lama"),
        Thought("The best way to predict the future is to create it.", "Peter Drucker"),
        Thought("Motivation gets you going, habit keeps you growing.", "John C. Maxwell"),
        Thought("Done is better than perfect.", "Sheryl Sandberg"),
        Thought("What we do today echoes through our tomorrows.", "Anonymous"),
        Thought("Small deeds done are better than great deeds planned.", "Peter Marshall"),
        Thought("Energy and persistence conquer all things.", "Benjamin Franklin"),
        Thought("The future depends on what you do today.", "Mahatma Gandhi"),
        Thought("Dream big. Start small. Act now.", "Robin Sharma"),
        Thought("Discipline is choosing between what you want now and what you want most.", "Abraham Lincoln"),
        Thought("Success is the sum of small efforts repeated day in and day out.", "Robert Collier"),
        Thought("Either you run the day or the day runs you.", "Jim Rohn"),
        Thought("Don't watch the clock; do what it does. Keep going.", "Sam Levenson"),
        Thought("The only way to do great work is to love what you do.", "Steve Jobs"),
        Thought("Begin anywhere.", "John Cage"),
        Thought("If you spend too long thinking about a thing, you'll never get it done.", "Bruce Lee"),
        Thought("Inspiration exists, but it has to find you working.", "Pablo Picasso"),
        Thought("Whatever you can do, or dream you can, begin it.", "Goethe"),
        Thought("The harder you work for something, the greater you'll feel when you achieve it.", "Anonymous"),
        Thought("A goal without a plan is just a wish.", "Antoine de Saint-Exupéry"),
        Thought("Great things are not done by impulse, but by a series of small things.", "Vincent van Gogh"),
        Thought("Doing is the engine of progress.", "Anonymous"),
        Thought("Make each day your masterpiece.", "John Wooden"),
        Thought("You miss 100% of the shots you don't take.", "Wayne Gretzky"),
        Thought("Act as if what you do makes a difference. It does.", "William James"),
        Thought("Nothing will work unless you do.", "Maya Angelou"),
        Thought("The reward for work well done is the opportunity to do more.", "Jonas Salk"),
        Thought("Focus on being productive instead of busy.", "Tim Ferriss"),
        Thought("Today is the first step toward tomorrow's accomplishment.", "Anonymous"),
        Thought("Progress, not perfection.", "Anonymous"),
        Thought("Knowing is not enough; we must apply.", "Goethe"),
        Thought("The man who moves a mountain begins by carrying away small stones.", "Confucius"),
        Thought("Be not afraid of going slowly; be afraid only of standing still.", "Chinese Proverb"),
        Thought("Your life does not get better by chance, it gets better by change.", "Jim Rohn"),
        Thought("Set a goal so big you can't achieve it until you grow into it.", "Anonymous"),
        Thought("Things may come to those who wait, but only the things left by those who hustle.", "Abraham Lincoln"),
        Thought("Believe you can and you're halfway there.", "Theodore Roosevelt"),
        Thought("Strive for progress, not perfection.", "Anonymous"),
        Thought("Don't count the days, make the days count.", "Muhammad Ali"),
        Thought("Everything you've ever wanted is on the other side of fear.", "George Addair"),
        Thought("Start now. Start where you are. Start with fear. Just start.", "Anonymous"),
        Thought("Discipline equals freedom.", "Jocko Willink"),
        Thought("A little progress each day adds up to big results.", "Anonymous"),
        Thought("The best preparation for tomorrow is doing your best today.", "H. Jackson Brown Jr."),
        Thought("Hard work beats talent when talent doesn't work hard.", "Tim Notke"),
        Thought("Fall seven times, stand up eight.", "Japanese Proverb"),
        Thought("Great acts are made up of small deeds.", "Lao Tzu"),
        Thought("The only limit to our realization of tomorrow is our doubts of today.", "Franklin D. Roosevelt"),
        Thought("Action cures fear.", "David J. Schwartz"),
        Thought("Whether you think you can or you think you can't, you're right.", "Henry Ford"),
        Thought("Do something today that your future self will thank you for.", "Anonymous"),
        Thought("Don't wait. The time will never be just right.", "Napoleon Hill"),
        Thought("Persistence guarantees that results are inevitable.", "Paramahansa Yogananda"),
        Thought("Courage is the commitment to begin without any guarantee of success.", "Goethe"),
        Thought("You are never too old to set another goal or to dream a new dream.", "C.S. Lewis"),
        Thought("Stop wishing. Start doing.", "Anonymous"),
        Thought("It is not the mountain we conquer, but ourselves.", "Edmund Hillary"),
        Thought("Real change, enduring change, happens one step at a time.", "Ruth Bader Ginsburg"),
        Thought("Tough times never last, but tough people do.", "Robert H. Schuller"),
        Thought("Opportunities don't happen. You create them.", "Chris Grosser"),
        Thought("If you get tired, learn to rest, not to quit.", "Banksy"),
        Thought("The expert in anything was once a beginner.", "Helen Hayes"),
        Thought("Genius is one percent inspiration and ninety-nine percent perspiration.", "Thomas Edison"),
        Thought("Do the hard jobs first. The easy jobs will take care of themselves.", "Dale Carnegie"),
        Thought("Don't limit your challenges. Challenge your limits.", "Anonymous"),
        Thought("There is no substitute for hard work.", "Thomas Edison"),
        Thought("Each morning we are born again. What we do today matters most.", "Buddha"),
        Thought("Create the things you wish existed.", "Anonymous"),
        Thought("Productivity is never an accident. It is the result of commitment.", "Paul J. Meyer"),
        Thought("The difference between try and triumph is a little 'umph'.", "Marvin Phillips"),
        Thought("Doing the best at this moment puts you in the best place for the next moment.", "Oprah Winfrey"),
        Thought("Don't be busy. Be productive.", "Anonymous"),
        Thought("Discipline is the bridge between goals and accomplishment.", "Jim Rohn"),
        Thought("Wake up with determination. Go to bed with satisfaction.", "Anonymous"),
        Thought("The most effective way to do it, is to do it.", "Amelia Earhart"),
        Thought("Take the first step in faith. You don't have to see the whole staircase.", "Martin Luther King Jr."),
        Thought("Success usually comes to those too busy to be looking for it.", "Henry David Thoreau"),
        Thought("Either write something worth reading or do something worth writing.", "Benjamin Franklin"),
        Thought("Don't let yesterday take up too much of today.", "Will Rogers"),
        Thought("It's not whether you get knocked down, it's whether you get up.", "Vince Lombardi"),
        Thought("Aim for the moon. If you miss, you may hit a star.", "W. Clement Stone"),
        Thought("Work hard in silence, let your success be the noise.", "Frank Ocean"),
        Thought("Your future is created by what you do today, not tomorrow.", "Robert Kiyosaki"),
        Thought("Setting goals is the first step in turning the invisible into the visible.", "Tony Robbins"),
        Thought("The best revenge is massive success.", "Frank Sinatra"),
        Thought("If it is important to you, you will find a way.", "Anonymous"),
        Thought("Turn your wounds into wisdom.", "Oprah Winfrey"),
        Thought("Be so good they can't ignore you.", "Steve Martin"),
    )
}
