import SwiftUI
import SideQuestKit

// MARK: - Bucket management screen (Req 9.1–9.5)
//
// Lists the account's buckets from the repository's reactive stream and lets the
// user create, rename, and delete them through the `BucketManagementService`.
// Deleting a non-empty bucket raises the reassign-or-delete prompt when another
// bucket exists (Req 9.4) or the confirm-delete-items prompt when it is the last
// bucket (Req 9.5); empty buckets delete directly.

/// The buckets tab.
struct BucketsView: View {

    @StateObject private var viewModel: BucketsViewModel

    /// In-progress rename: the bucket being renamed and its edited name.
    @State private var renaming: Bucket?
    @State private var renameText: String = ""

    init(
        bucketRepository: BucketRepository,
        bucketManagement: BucketManagementService,
        authService: AuthService? = nil
    ) {
        _viewModel = StateObject(
            wrappedValue: BucketsViewModel(
                bucketRepository: bucketRepository,
                bucketManagement: bucketManagement,
                authService: authService
            )
        )
    }

    var body: some View {
        NavigationStack {
            List {
                Section("New bucket") {
                    HStack {
                        TextField("Bucket name", text: $viewModel.newBucketName)
                            .submitLabel(.done)
                            .onSubmit { create() }
                        Button(action: create) {
                            Image(systemName: "plus.circle.fill")
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .disabled(!viewModel.canCreate)
                        .accessibilityLabel("Add bucket")
                    }
                }

                Section("Buckets") {
                    if viewModel.buckets.isEmpty {
                        Text("No buckets yet. Add one above.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(viewModel.buckets, id: \.id) { bucket in
                            Text(bucket.name)
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        viewModel.beginDelete(bucket)
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                    Button {
                                        renaming = bucket
                                        renameText = bucket.name
                                    } label: {
                                        Label("Rename", systemImage: "pencil")
                                    }
                                    .tint(.blue)
                                }
                        }
                    }
                }
            }
            .navigationTitle("Buckets")
            .task { await viewModel.observe() }
            // Rename prompt.
            .alert("Rename bucket", isPresented: renamingBinding) {
                TextField("Bucket name", text: $renameText)
                Button("Save") {
                    if let bucket = renaming {
                        viewModel.rename(bucket, to: renameText)
                    }
                    renaming = nil
                }
                Button("Cancel", role: .cancel) { renaming = nil }
            }
            // Delete decision prompt (Req 9.4, 9.5).
            .confirmationDialog(
                deletePromptTitle,
                isPresented: deletePromptBinding,
                titleVisibility: .visible,
                presenting: viewModel.pendingDelete
            ) { pending in
                deleteActions(for: pending)
            } message: { pending in
                Text(deletePromptMessage(for: pending))
            }
            // Validation / save errors.
            .alert(
                "Couldn’t complete",
                isPresented: errorBinding,
                actions: { Button("OK", role: .cancel) { viewModel.dismissError() } },
                message: {
                    if let message = viewModel.errorMessage {
                        Text(message)
                    }
                }
            )
        }
    }

    // MARK: - Actions

    private func create() {
        Task { await viewModel.createBucket() }
    }

    @ViewBuilder
    private func deleteActions(for pending: BucketsViewModel.PendingDelete) -> some View {
        switch pending.decision {
        case .requiresReassignOrDelete(_, let candidates):
            ForEach(candidates, id: \.id) { target in
                Button("Move items to “\(target.name)”") {
                    viewModel.confirmDelete(pending, strategy: .reassign(targetBucketId: target.id))
                }
            }
            Button("Delete items", role: .destructive) {
                viewModel.confirmDelete(pending, strategy: .deleteItems)
            }
            Button("Cancel", role: .cancel) { viewModel.cancelDelete() }
        case .requiresConfirmDeleteItems:
            Button("Delete items", role: .destructive) {
                viewModel.confirmDelete(pending, strategy: .deleteItems)
            }
            Button("Cancel", role: .cancel) { viewModel.cancelDelete() }
        case .deleteDirectly:
            // Handled directly in the view model; no prompt is presented.
            EmptyView()
        }
    }

    private var deletePromptTitle: String {
        guard let pending = viewModel.pendingDelete else { return "Delete bucket" }
        return "Delete “\(pending.bucket.name)”"
    }

    private func deletePromptMessage(for pending: BucketsViewModel.PendingDelete) -> String {
        switch pending.decision {
        case .requiresReassignOrDelete(let count, _):
            return "This bucket has \(count) item(s). Move them to another bucket or delete them."
        case .requiresConfirmDeleteItems(let count):
            return "This is your last bucket and has \(count) item(s). Deleting it deletes those items."
        case .deleteDirectly:
            return ""
        }
    }

    // MARK: - Bindings

    private var renamingBinding: Binding<Bool> {
        Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })
    }

    private var deletePromptBinding: Binding<Bool> {
        Binding(
            get: { viewModel.pendingDelete != nil },
            set: { if !$0 { viewModel.cancelDelete() } }
        )
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.dismissError() } }
        )
    }
}
