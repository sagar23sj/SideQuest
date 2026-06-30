import SwiftUI
import SideQuestKit

// MARK: - Action-plan screen (Req 9.8, 9.9, 9.10)
//
// The screen for breaking an Action_Item into 1...100 ordered sub-actions. It
// lets the user add, edit, complete, reorder, and remove sub-actions (Req 9.8),
// shows the completed/total progress (Req 9.9), and — when every sub-action is
// done — offers to mark the parent item completed (Req 9.10).
//
// Split into:
//   * `ActionPlanView` — the stateful container. It owns the
//     ``ActionPlanViewModel``, hosts the completion + error alerts, and renders
//     the content.
//   * `ActionPlanContentView` — a stateless, previewable view that binds the
//     draft sub-actions and reports intents through closures.

/// The stateful action-plan container: owns the view model and hosts the
/// completion/error prompts (Req 9.8–9.10).
struct ActionPlanView: View {

    @StateObject private var viewModel: ActionPlanViewModel
    @Environment(\.dismiss) private var dismiss

    init(
        item: ActionItem,
        planRepository: ActionPlanRepository,
        itemRepository: ActionItemRepository
    ) {
        _viewModel = StateObject(
            wrappedValue: ActionPlanViewModel(
                item: item,
                planRepository: planRepository,
                itemRepository: itemRepository
            )
        )
    }

    var body: some View {
        ActionPlanContentView(
            itemTitle: viewModel.item.title,
            subActions: $viewModel.subActions,
            newSubActionText: $viewModel.newSubActionText,
            progress: viewModel.progress,
            canAddSubAction: viewModel.canAddSubAction,
            canSave: viewModel.canSave,
            onAdd: { viewModel.addSubAction() },
            onToggle: { viewModel.toggleCompleted(id: $0) },
            onDelete: { viewModel.deleteSubActions(at: $0) },
            onMove: { viewModel.moveSubActions(from: $0, to: $1) },
            onSave: {
                if viewModel.save() { dismiss() }
            }
        )
        // Offer to complete the parent item once every sub-action is done
        // (Req 9.10). Confirming marks it completed; declining leaves it as-is.
        .alert(
            "All steps done",
            isPresented: $viewModel.showCompletionPrompt,
            actions: {
                Button("Mark item completed") { viewModel.markItemCompleted() }
                Button("Not yet", role: .cancel) { viewModel.dismissCompletionPrompt() }
            },
            message: {
                Text("You’ve completed every step. Mark “\(viewModel.item.title)” as completed?")
            }
        )
        // "Not saved" indication for a failed plan/item write (Req 5.8).
        .alert(
            "Not saved",
            isPresented: Binding(
                get: { viewModel.errorMessage != nil },
                set: { stillPresented in
                    if !stillPresented { viewModel.dismissError() }
                }
            ),
            actions: {
                Button("OK", role: .cancel) { viewModel.dismissError() }
            },
            message: {
                if let message = viewModel.errorMessage {
                    Text(message)
                }
            }
        )
        .onChange(of: viewModel.didMarkItemCompleted) { completed in
            if completed { dismiss() }
        }
    }
}

/// The stateless action-plan UI: renders the draft sub-actions and reports
/// intents through closures (Req 9.8, 9.9). Free of stores/streams so it is
/// trivially previewable.
struct ActionPlanContentView: View {

    let itemTitle: String
    @Binding var subActions: [SubAction]
    @Binding var newSubActionText: String
    let progress: ActionPlanProgress
    let canAddSubAction: Bool
    let canSave: Bool
    let onAdd: () -> Void
    let onToggle: (String) -> Void
    let onDelete: (IndexSet) -> Void
    let onMove: (IndexSet, Int) -> Void
    let onSave: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ProgressSummary(progress: progress)
                }

                Section("Steps") {
                    if subActions.isEmpty {
                        Text("Add at least one step to build a plan.")
                            .foregroundStyle(.secondary)
                    } else {
                        // `ForEach($subActions)` yields per-element bindings so
                        // each step's text is editable inline; `id: \.id` keeps
                        // identity stable across reorders (Req 9.8).
                        ForEach($subActions, id: \.id) { $subAction in
                            SubActionRow(
                                subAction: $subAction,
                                onToggle: { onToggle(subAction.id) }
                            )
                        }
                        .onDelete(perform: onDelete)
                        .onMove(perform: onMove)
                    }
                }

                Section("Add a step") {
                    HStack {
                        TextField("New step", text: $newSubActionText)
                            .submitLabel(.done)
                            .onSubmit(onAdd)
                        Button(action: onAdd) {
                            Image(systemName: "plus.circle.fill")
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .disabled(!canAddSubAction)
                        .accessibilityLabel("Add step")
                    }
                }
            }
            .navigationTitle("Action plan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                // EditButton enables drag-to-reorder and swipe-to-delete on the
                // steps list (Req 9.8).
                ToolbarItem(placement: .navigationBarLeading) {
                    EditButton()
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: onSave)
                        // A plan needs 1...100 steps to save (Req 9.8).
                        .disabled(!canSave)
                }
            }
        }
    }
}

/// The completed/total summary at the top of the plan (Req 9.9).
private struct ProgressSummary: View {

    let progress: ActionPlanProgress

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Completed")
                    .font(.headline)
                Spacer()
                Text("\(progress.completed) of \(progress.total)")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            if progress.total > 0 {
                ProgressView(
                    value: Double(progress.completed),
                    total: Double(progress.total)
                )
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Plan progress")
        .accessibilityValue("\(progress.completed) of \(progress.total) steps completed")
    }
}

/// A single editable step: a completion toggle plus an inline-editable title
/// (Req 9.8). Color is never the only signal — the toggle exposes its state to
/// assistive technologies.
private struct SubActionRow: View {

    @Binding var subAction: SubAction
    let onToggle: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onToggle) {
                Image(systemName: subAction.completed ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(subAction.completed ? Color.green : Color.secondary)
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(subAction.completed ? "Mark step not completed" : "Mark step completed")

            TextField("Step", text: $subAction.text)
                .strikethrough(subAction.completed, color: .secondary)
                .foregroundStyle(subAction.completed ? .secondary : .primary)
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Previews

#Preview("Plan in progress") {
    ActionPlanPreview(subActions: [
        SubAction(id: "s1", text: "Outline the talk", order: 0, completed: true),
        SubAction(id: "s2", text: "Draft slides", order: 1, completed: true),
        SubAction(id: "s3", text: "Rehearse", order: 2, completed: false)
    ])
}

#Preview("Empty plan") {
    ActionPlanPreview(subActions: [])
}

/// Hosts ``ActionPlanContentView`` with mutable preview state so the bindings,
/// progress, and gating render realistically without a store.
private struct ActionPlanPreview: View {

    @State var subActions: [SubAction]
    @State private var newText = ""

    var body: some View {
        ActionPlanContentView(
            itemTitle: "Give the conference talk",
            subActions: $subActions,
            newSubActionText: $newText,
            progress: ActionPlanProgress(
                completed: subActions.filter(\.completed).count,
                total: subActions.count
            ),
            canAddSubAction: !newText.trimmingCharacters(in: .whitespaces).isEmpty,
            canSave: (1...100).contains(subActions.count),
            onAdd: {},
            onToggle: { id in
                if let index = subActions.firstIndex(where: { $0.id == id }) {
                    subActions[index].completed.toggle()
                }
            },
            onDelete: { subActions.remove(atOffsets: $0) },
            onMove: { subActions.move(fromOffsets: $0, toOffset: $1) },
            onSave: {}
        )
    }
}
