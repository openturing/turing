import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurNLPEntity } from '../../model/nlp-entity.model';
import { NotifierService } from 'angular-notifier';
import { TurNLPEntityService } from '../../service/nlp-entity.service';
import { ActivatedRoute, Router } from '@angular/router';
import { FormControl, Validators } from '@angular/forms';

@Component({
  selector: 'nlp-entity-page',
  templateUrl: './nlp-entity-page.component.html'
})
export class TurNLPEntityPageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turNLPEntity: Observable<TurNLPEntity>;
  private newObject: boolean = false;

  portControl = new FormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turNLPEntityService: TurNLPEntityService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {

    let id: string = this.activatedRoute.snapshot.paramMap.get('id') || "";

    this.newObject = (id.toLowerCase() === 'new');

    this.turNLPEntity = this.newObject ? this.turNLPEntityService.getStructure() : this.turNLPEntityService.get(id);
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create NLP entity" : "Update NLP entity";
  }

  getTurNLPEntity(): Observable<TurNLPEntity> {
    return this.turNLPEntity;
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  ngOnInit(): void {
  }

  public save(_turNLPEntity: TurNLPEntity){
    this.turNLPEntityService.save(_turNLPEntity, this.newObject).subscribe(
      (turNLPEntity: TurNLPEntity) => {


        let message: string = this.newObject ? " NLP entity was created." : " NLP entity was updated.";

        _turNLPEntity = turNLPEntity;

        this.notifier.notify("success", turNLPEntity.name.concat(message));

        this.router.navigate(['/console/nlp/entity']);
      },
      response => {
        this.notifier.notify("error", "NLP entity was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }

  public delete(_turNLPEntity: TurNLPEntity) {
    this.turNLPEntityService.delete(_turNLPEntity).subscribe(
      () => {
        this.notifier.notify("success", _turNLPEntity.name.concat(" NLP entity was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/console/nlp/entity']);
      },
      response => {
        this.notifier.notify("error", "NLP entity was error: " + response);
      },
     );

  }
}
