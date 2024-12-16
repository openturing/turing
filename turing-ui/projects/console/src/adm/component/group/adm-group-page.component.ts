import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurAdmGroup } from '../../model/adm-group.model';
import { NotifierService } from 'angular-notifier';
import { TurAdmGroupService } from '../../service/adm-group.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, Validators } from '@angular/forms';

@Component({
  selector: 'adm-group-page',
  templateUrl: './adm-group-page.component.html'
})
export class TurAdmGroupPageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turAdmGroup: Observable<TurAdmGroup>;
  private newObject: boolean = false;

  portControl = new FormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turAdmGroupService: TurAdmGroupService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {

    let id: string = this.activatedRoute.snapshot.paramMap.get('id') || "";

    this.newObject = ( id != null && id.toLowerCase() === 'new');

    this.turAdmGroup = this.newObject ? this.turAdmGroupService.getStructure() : this.turAdmGroupService.get(id);

  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create group" : "Update group";
  }

  getTurAdmGroup(): Observable<TurAdmGroup> {
    return this.turAdmGroup;
  }

  ngOnInit(): void {
  }

  public save(_turAdmGroup: TurAdmGroup) {
    this.turAdmGroupService.save(_turAdmGroup, this.newObject).subscribe(
      (turAdmGroup: TurAdmGroup) => {
        let message: string = this.newObject ? " group was created." : " group was updated.";

        _turAdmGroup = turAdmGroup;

        this.notifier.notify("success", turAdmGroup.name.concat(message));

        this.router.navigate(['/adm/group']);
      },
      response => {
        this.notifier.notify("error", "group was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turAdmGroup: TurAdmGroup) {
    this.turAdmGroupService.delete(_turAdmGroup).subscribe(
      (turAdmGroup: TurAdmGroup) => {
        _turAdmGroup = turAdmGroup;
        this.notifier.notify("success", turAdmGroup.name.concat(" group was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
      },
      response => {
        this.notifier.notify("error", "group was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }
}
