import SwiftUI
import SideQuestKit

// MARK: - Timeframe screen (Req 9.6, 9.7)
//
// The screen for choosing/changing an Action_Item's timeframe. It offers the
// full option set (Req 9.6) and rejects a past specific date with the message
// from `Domain.validateTimeframe` (task 4.4, Req 9.7), keeping Save disabled
// until the selection is valid. The actual resolution + validation lives in
// ``EditTimeframeViewModel``; this view binds to it and renders
// ``TimeframePicker``.

/// The stateful timeframe screen: owns the view model and dismisses itself once
/// the timeframe is saved.
struct EditTimeframeView: View {

    @StateObject private var viewModel: EditTimeframeViewModel
    @Environment(\.dismiss) private var dismiss

    init(item: ActionItem, itemRepository: ActionItemRepository) {
        _viewModel = StateObject(
            wrappedValue: EditTimeframeViewModel(item: item, itemRepository: itemRepository)
        )
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Item") {
                    Text(viewModel.item.title)
                        .font(.body)
                        .lineLimit(3)
                }

                TimeframePicker(
                    option: $viewModel.option,
                    date: $viewModel.date,
                    validationMessage: viewModel.validationMessage
                )
            }
            .navigationTitle("Timeframe")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { viewModel.save() }
                        // Disabled for an empty selection or a past specific
                        // date (Req 9.7).
                        .disabled(!viewModel.canSave)
                }
            }
            .alert(
                "Timeframe not saved",
                isPresented: Binding(
                    get: { viewModel.saveErrorMessage != nil },
                    set: { stillPresented in
                        if !stillPresented { viewModel.dismissSaveError() }
                    }
                ),
                actions: {
                    Button("OK", role: .cancel) { viewModel.dismissSaveError() }
                },
                message: {
                    if let message = viewModel.saveErrorMessage {
                        Text(message)
                    }
                }
            )
            // Dismiss once the timeframe has been persisted.
            .onChange(of: viewModel.didSave) { saved in
                if saved { dismiss() }
            }
        }
    }
}
